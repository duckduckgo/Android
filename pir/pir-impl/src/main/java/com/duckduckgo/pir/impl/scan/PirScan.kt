/*
 * Copyright (c) 2025 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.pir.impl.scan

import android.content.Context
import android.webkit.WebView
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirConstants.DEFAULT_PROFILE_QUERIES
import com.duckduckgo.pir.impl.callbacks.PirCallbacks
import com.duckduckgo.pir.impl.common.BrokerStepsParser
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.PirActionsRunner
import com.duckduckgo.pir.impl.common.PirJob
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirJobConstants.MAX_DETACHED_WEBVIEW_COUNT
import com.duckduckgo.pir.impl.common.PirWebViewDataCleaner
import com.duckduckgo.pir.impl.common.RealPirActionsRunner
import com.duckduckgo.pir.impl.common.splitIntoParts
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.scripts.PirCssScriptLoader
import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.db.EventType
import com.duckduckgo.pir.impl.store.db.PirEventLog
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

/**
 * This class is the main entry point for any scan execution (manual or scheduled)
 */
interface PirScan {
    suspend fun executeScanForJobs(
        jobRecords: List<ScanJobRecord>,
        context: Context,
        runType: RunType,
    ): Result<Unit>

    /**
     * NOTE: This method should only be used for internal dev functionality only.
     * This method can be used to execute pir scan for a given list of [brokers] names.
     * You DO NOT need to set any dispatcher to call this suspend function. It is already set to run on IO.
     *
     * @param brokers List of broker names
     * @param context Context in which we want to create the detached WebView from
     */
    suspend fun execute(
        brokers: List<String>,
        context: Context,
        runType: RunType,
    ): Result<Unit>

    /**
     * This method should only be used if we want to debug scan and pass in a specific [webView] which will allow us to see the run in action.
     * Do not use this for non-debug scenarios.
     * You DO NOT need to set any dispatcher to call this suspend function. It is already set to run on IO.
     *
     * @param brokers - List of broker names
     * @param webView - attached/visible WebView in which we will run the scan.
     */
    suspend fun debugExecute(
        brokers: List<String>,
        webView: WebView,
    ): Result<Unit>

    /**
     * NOTE: This method should only be used for internal dev functionality only.
     * This method can be used to execute pir scan for all active brokers (from json).
     * You DO NOT need to set any dispatcher to call this suspend function. It is already set to run on IO.
     *
     * @param context Context in which we want to create the detached WebView from
     */
    suspend fun executeAllBrokers(
        context: Context,
        runType: RunType,
    ): Result<Unit>

    /**
     * This method takes care of stopping the scan and cleaning up resources used.
     */
    fun stop()
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = PirScan::class,
)
@SingleInstanceIn(AppScope::class)
class RealPirScan @Inject constructor(
    private val repository: PirRepository,
    private val eventsRepository: PirEventsRepository,
    private val brokerStepsParser: BrokerStepsParser,
    private val pirCssScriptLoader: PirCssScriptLoader,
    private val pirActionsRunnerFactory: RealPirActionsRunner.Factory,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val webViewDataCleaner: PirWebViewDataCleaner,
    callbacks: PluginPoint<PirCallbacks>,
) : PirScan, PirJob(callbacks) {

    private val runners: MutableList<PirActionsRunner> = mutableListOf()
    private var maxWebViewCount = 1

    override suspend fun executeScanForJobs(
        jobRecords: List<ScanJobRecord>,
        context: Context,
        runType: RunType,
    ) = withContext(dispatcherProvider.io()) {
        logcat { "PIR-SCAN: Running scan on the following records: $jobRecords on ${Thread.currentThread().name}" }
        onJobStarted()

        val startTimeMillis = currentTimeProvider.currentTimeMillis()
        emitScanStartPixel(runType)

        val activeBrokers = repository.getAllActiveBrokerObjects().associateBy { it.name }
        if (activeBrokers.isEmpty()) {
            logcat { "PIR-SCAN: No active brokers here." }
            completeScan(runType)
            return@withContext Result.success(Unit)
        }

        if (jobRecords.isEmpty()) {
            logcat { "PIR-SCAN: Nothing to scan here." }
            completeScan(runType)
            return@withContext Result.success(Unit)
        }

        cleanPreviousRun()

        val processedJobRecords = processJobRecords(jobRecords, activeBrokers)
        logcat { "PIR-SCAN: Total processed records ${processedJobRecords.size}" }

        if (processedJobRecords.isEmpty()) {
            logcat { "PIR-SCAN: No job records." }
            completeScan(runType)
            return@withContext Result.success(Unit)
        }

        val script = pirCssScriptLoader.getScript()
        maxWebViewCount = minOf(processedJobRecords.size, MAX_DETACHED_WEBVIEW_COUNT)

        logcat { "PIR-SCAN: Attempting to create $maxWebViewCount parallel runners on ${Thread.currentThread().name}" }
        // Initiate runners
        repeat(maxWebViewCount) {
            runners.add(pirActionsRunnerFactory.create(context, script, runType))
        }

        val jobRecordsParts = processedJobRecords.splitIntoParts(maxWebViewCount)

        logcat { "PIR-SCAN: Total parts ${jobRecordsParts.size}" }

        // Execute the steps in parallel
        jobRecordsParts.mapIndexed { index, partSteps ->
            logcat { "PIR-SCAN: Record part [$index] -> ${partSteps.size}" }
            logcat { "PIR-SCAN: Record part [$index] breakdown -> ${partSteps.map { it.first.id to it.second.broker.name }}" }
            // We want to run the runners in parallel but wait for everything to complete before we proceed
            async {
                partSteps.forEach { (profile, step) ->
                    logcat { "PIR-SCAN: Start scan on runner=$index for profile=$profile with step=$step" }
                    runners[index].start(profile, listOf(step))
                    runners[index].stop()
                    logcat { "PIR-SCAN: Finish scan on runner=$index for profile=$profile with step=$step" }
                }
            }
        }.awaitAll()

        completeScan(runType)
        return@withContext Result.success(Unit)
    }

    override suspend fun debugExecute(
        brokers: List<String>,
        webView: WebView,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        onJobStarted()
        if (runners.isNotEmpty()) {
            cleanPreviousRun()
        }
        // Multiple profile support (includes deprecated profiles as we need to process opt-out for them if there are extracted profiles)
        val profileQueries = obtainProfiles()

        logcat { "PIR-SCAN: Running debug scan for $brokers on profiles: $profileQueries on ${Thread.currentThread().name}" }

        runners.add(
            pirActionsRunnerFactory.create(
                webView.context,
                pirCssScriptLoader.getScript(),
                RunType.MANUAL,
            ),
        )

        val activeBrokers = repository.getAllActiveBrokerObjects().associateBy { it.name }

        // Prepare a list of all broker steps that need to be run
        val brokerScanSteps = brokers.mapNotNull { brokerName ->
            val broker = activeBrokers[brokerName] ?: return@mapNotNull null
            repository.getBrokerScanSteps(brokerName)?.run {
                brokerStepsParser.parseStep(broker, this)
            }
        }.filter {
            it.isNotEmpty()
        }.flatten()

        // Map broker steps with their associated profile queries
        val allSteps = profileQueries.map { profileQuery ->
            brokerScanSteps.map { scanStep ->
                profileQuery to scanStep
            }
        }.flatten()

        // Execute each step sequentially on the single runner
        allSteps.forEach { (profileQuery, step) ->
            logcat { "PIR-SCAN: Start thread=${Thread.currentThread().name}, profile=$profileQuery and step=$step" }
            runners[0].startOn(webView, profileQuery, listOf(step))
            runners[0].stop()
            logcat { "PIR-SCAN: Finish thread=${Thread.currentThread().name}, profile=$profileQuery and step=$step" }
        }

        logcat { "PIR-SCAN: Debug scan completed for all profiles" }

        onJobCompleted()
        webViewDataCleaner.cleanWebViewData()
        return@withContext Result.success(Unit)
    }

    private suspend fun processJobRecords(
        jobRecords: List<ScanJobRecord>,
        activeBrokers: Map<String, Broker>,
    ): List<Pair<ProfileQuery, BrokerStep>> {
        val relevantBrokerSteps = jobRecords.mapTo(mutableSetOf()) { it.brokerName }.mapNotNull {
            val broker = activeBrokers[it] ?: return@mapNotNull null

            val steps = repository.getBrokerScanSteps(it)?.run {
                brokerStepsParser.parseStep(broker, this)
            }
            if (!steps.isNullOrEmpty()) {
                it to steps[0]
            } else {
                null
            }
        }.toMap()

        logcat { "PIR-SCAN: Relevant broker steps $relevantBrokerSteps" }

        if (relevantBrokerSteps.isEmpty()) {
            logcat { "PIR-SCAN: No steps available." }
            return emptyList()
        }

        val relevantProfileIds = jobRecords.mapTo(mutableSetOf()) { it.userProfileId }
        // Multiple profile support (includes deprecated profiles as we need to process opt-out for them if there are extracted profiles)
        val relevantProfiles = obtainProfiles()
            .filter {
                it.id in relevantProfileIds
            }.associateBy { it.id }

        logcat { "PIR-SCAN: Relevant profileQueries $relevantProfiles" }

        if (relevantProfiles.isEmpty()) {
            logcat { "PIR-SCAN: Nothing to scan here. Can't map jobrecord profiles." }
            return emptyList()
        }

        val processedJobRecords = jobRecords.mapNotNull {
            val profileQuery = relevantProfiles[it.userProfileId]
            val brokerSteps = relevantBrokerSteps[it.brokerName]
            if (profileQuery != null && brokerSteps != null) {
                profileQuery to brokerSteps
            } else {
                null
            }
        }

        return processedJobRecords
    }

    private suspend fun completeScan(
        runType: RunType,
    ) {
        logcat { "PIR-SCAN: Scan completed for all runners on all profiles" }
        webViewDataCleaner.cleanWebViewData()
        emitScanCompletedPixel(
            runType,
        )
        onJobCompleted()
    }

    override suspend fun execute(
        brokers: List<String>,
        context: Context,
        runType: RunType,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        onJobStarted()

        emitScanStartPixel(runType)
        cleanPreviousRun()

        // Multiple profile support (includes deprecated profiles as we need to process opt-out for them if there are extracted profiles)
        val profileQueries = obtainProfiles()

        logcat { "PIR-SCAN: Running scan on profiles: $profileQueries on ${Thread.currentThread().name}" }

        val script = pirCssScriptLoader.getScript()
        maxWebViewCount = minOf(brokers.size * profileQueries.size, MAX_DETACHED_WEBVIEW_COUNT)

        logcat { "PIR-SCAN: Attempting to create $maxWebViewCount parallel runners on ${Thread.currentThread().name}" }

        // Initiate runners
        repeat(maxWebViewCount) {
            runners.add(pirActionsRunnerFactory.create(context, script, runType))
        }

        val activeBrokers = repository.getAllActiveBrokerObjects().associateBy { it.name }

        // Prepare a list of all broker steps that need to be run
        val brokerScanSteps = brokers.mapNotNull { brokerName ->
            val broker = activeBrokers[brokerName] ?: return@mapNotNull null
            repository.getBrokerScanSteps(brokerName)?.run {
                brokerStepsParser.parseStep(broker, this)
            }
        }.filter {
            it.isNotEmpty()
        }.flatten()

        // Combine the broker steps with each profile and split into equal parts
        val stepsPerRunner = profileQueries.map { profileQuery ->
            brokerScanSteps.map { scanStep ->
                profileQuery to scanStep
            }
        }.flatten()
            .splitIntoParts(maxWebViewCount)

        // Execute the steps in parallel
        stepsPerRunner.mapIndexed { index, partSteps ->
            // We want to run the runners in parallel but wait for everything to complete before we proceed
            async {
                partSteps.forEach { (profile, step) ->
                    logcat { "PIR-SCAN: Start scan on runner=$index for profile=$profile with step=$step" }
                    runners[index].start(profile, listOf(step))
                    runners[index].stop()
                    logcat { "PIR-SCAN: Finish scan on runner=$index for profile=$profile with step=$step" }
                }
            }
        }.awaitAll()

        completeScan(runType)
        return@withContext Result.success(Unit)
    }

    private suspend fun cleanPreviousRun() {
        if (runners.isNotEmpty()) {
            runners.forEach {
                it.stop()
            }
            runners.clear()
        }
        eventsRepository.deleteAllScanResults()
    }

    private suspend fun obtainProfiles(): List<ProfileQuery> {
        return repository.getAllUserProfileQueries().ifEmpty {
            DEFAULT_PROFILE_QUERIES
        }
    }

    override suspend fun executeAllBrokers(
        context: Context,
        runType: RunType,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        val brokers = repository.getAllBrokersForScan()
        return@withContext execute(brokers, context, runType)
    }

    override fun stop() {
        logcat { "PIR-SCAN: Stopping all runners" }
        runners.forEach {
            it.stop()
        }
        runners.clear()
        onJobStopped()
        webViewDataCleaner.cleanWebViewData()
    }

    private suspend fun emitScanStartPixel(runType: RunType) {
        if (runType == RunType.MANUAL) {
            eventsRepository.saveEventLog(
                PirEventLog(
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    eventType = EventType.MANUAL_SCAN_STARTED,
                ),
            )
        } else {
            eventsRepository.saveEventLog(
                PirEventLog(
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    eventType = EventType.SCHEDULED_SCAN_STARTED,
                ),
            )
        }
    }

    private suspend fun emitScanCompletedPixel(
        runType: RunType,
    ) {
        if (runType == RunType.MANUAL) {
            eventsRepository.saveEventLog(
                PirEventLog(
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    eventType = EventType.MANUAL_SCAN_COMPLETED,
                ),
            )
        } else {
            eventsRepository.saveEventLog(
                PirEventLog(
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    eventType = EventType.SCHEDULED_SCAN_COMPLETED,
                ),
            )
        }
    }
}
