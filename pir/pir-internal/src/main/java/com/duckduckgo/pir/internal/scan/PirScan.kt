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

package com.duckduckgo.pir.internal.scan

import android.content.Context
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.PirInternalConstants.DEFAULT_PROFILE_QUERIES
import com.duckduckgo.pir.internal.callbacks.PirCallbacks
import com.duckduckgo.pir.internal.common.BrokerStepsParser
import com.duckduckgo.pir.internal.common.PirActionsRunner
import com.duckduckgo.pir.internal.common.PirJob
import com.duckduckgo.pir.internal.common.PirJob.RunType
import com.duckduckgo.pir.internal.common.PirJobConstants.MAX_DETACHED_WEBVIEW_COUNT
import com.duckduckgo.pir.internal.common.RealPirActionsRunner
import com.duckduckgo.pir.internal.common.splitIntoParts
import com.duckduckgo.pir.internal.models.ProfileQuery
import com.duckduckgo.pir.internal.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.internal.pixels.PirPixelSender
import com.duckduckgo.pir.internal.scripts.PirCssScriptLoader
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.db.EventType
import com.duckduckgo.pir.internal.store.db.PirEventLog
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import logcat.logcat

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
    private val brokerStepsParser: BrokerStepsParser,
    private val pirCssScriptLoader: PirCssScriptLoader,
    private val pirActionsRunnerFactory: RealPirActionsRunner.Factory,
    private val pixelSender: PirPixelSender,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatcherProvider: DispatcherProvider,
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

        if (jobRecords.isEmpty()) {
            logcat { "PIR-SCAN: Nothing to scan here." }
            completeScan(runType, startTimeMillis)
            return@withContext Result.success(Unit)
        }

        cleanPreviousRun()

        val relevantBrokerSteps = jobRecords.map { it.brokerName }.distinct().mapNotNull {
            val steps = repository.getBrokerScanSteps(it)?.run {
                brokerStepsParser.parseStep(it, this)
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
            completeScan(runType, startTimeMillis)
            return@withContext Result.success(Unit)
        }

        val allProfiles = obtainProfiles().associateBy { it.id }

        if (allProfiles.isEmpty()) {
            logcat { "PIR-SCAN: Nothing to scan here. No user profiles available." }
            completeScan(runType, startTimeMillis)
            return@withContext Result.success(Unit)
        }

        val relevantProfiles = jobRecords.map { it.userProfileId }.distinct().mapNotNull {
            val profileQuery = allProfiles[it]

            if (profileQuery != null) {
                it to allProfiles[it]
            } else {
                null
            }
        }.toMap()

        logcat { "PIR-SCAN: Relevant profileQueries $relevantProfiles" }

        if (relevantProfiles.isEmpty()) {
            logcat { "PIR-SCAN: Nothing to scan here. Can't map jobrecord profiles." }
            completeScan(runType, startTimeMillis)
            return@withContext Result.success(Unit)
        }

        val script = pirCssScriptLoader.getScript()
        maxWebViewCount = minOf(jobRecords.size, MAX_DETACHED_WEBVIEW_COUNT)

        logcat { "PIR-SCAN: Attempting to create $maxWebViewCount parallel runners on ${Thread.currentThread().name}" }
        var createCount = 0
        while (createCount != maxWebViewCount) {
            runners.add(
                pirActionsRunnerFactory.create(
                    context,
                    script,
                    runType,
                ),
            )
            createCount++
        }

        val jobRecordsParts = jobRecords.mapNotNull {
            val profileQuery = relevantProfiles[it.userProfileId]
            val brokerSteps = relevantBrokerSteps[it.brokerName]
            if (profileQuery != null && brokerSteps != null) {
                profileQuery to brokerSteps
            } else {
                null
            }
        }.splitIntoParts(maxWebViewCount)

        logcat { "PIR-SCAN: Total parts ${jobRecordsParts.size}" }

        // Execute the steps in parallel
        jobRecordsParts.mapIndexed { index, partSteps ->
            logcat { "PIR-SCAN: Record part [$index] -> ${partSteps.size}" }
            logcat { "PIR-SCAN: Record part [$index] breakdown -> ${partSteps.map { it.first.id to it.second.brokerName }}" }
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

        completeScan(runType, startTimeMillis)
        return@withContext Result.success(Unit)
    }

    private suspend fun completeScan(
        runType: RunType,
        startTimeMillis: Long,
    ) {
        logcat { "PIR-SCAN: Scan completed for all runners on all profiles" }
        emitScanCompletedPixel(
            runType,
            currentTimeProvider.currentTimeMillis() - startTimeMillis,
            maxWebViewCount,
        )
        onJobCompleted()
    }

    override suspend fun execute(
        brokers: List<String>,
        context: Context,
        runType: RunType,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        onJobStarted()

        val startTimeMillis = currentTimeProvider.currentTimeMillis()

        emitScanStartPixel(runType)
        cleanPreviousRun()

        val profileQueries = obtainProfiles()

        logcat { "PIR-SCAN: Running scan on profiles: $profileQueries on ${Thread.currentThread().name}" }

        val script = pirCssScriptLoader.getScript()
        maxWebViewCount = minOf(brokers.size * profileQueries.size, MAX_DETACHED_WEBVIEW_COUNT)

        logcat { "PIR-SCAN: Attempting to create $maxWebViewCount parallel runners on ${Thread.currentThread().name}" }

        // Initiate runners
        var createCount = 0
        while (createCount != maxWebViewCount) {
            runners.add(
                pirActionsRunnerFactory.create(
                    context,
                    script,
                    runType,
                ),
            )
            createCount++
        }

        // Prepare a list of all broker steps that need to be run
        val brokerScanSteps = brokers.mapNotNull { broker ->
            repository.getBrokerScanSteps(broker)?.run {
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

        completeScan(runType, startTimeMillis)
        return@withContext Result.success(Unit)
    }

    private suspend fun cleanPreviousRun() {
        if (runners.isNotEmpty()) {
            runners.forEach {
                it.stop()
            }
            runners.clear()
        }
        repository.deleteAllScanResults()
    }

    private suspend fun obtainProfiles(): List<ProfileQuery> {
        return repository.getUserProfileQueries().ifEmpty {
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
    }

    private suspend fun emitScanStartPixel(runType: RunType) {
        if (runType == RunType.MANUAL) {
            pixelSender.reportManualScanStarted()
            repository.saveScanLog(
                PirEventLog(
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    eventType = EventType.MANUAL_SCAN_STARTED,
                ),
            )
        } else {
            pixelSender.reportScheduledScanStarted()
            repository.saveScanLog(
                PirEventLog(
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    eventType = EventType.SCHEDULED_SCAN_STARTED,
                ),
            )
        }
    }

    private suspend fun emitScanCompletedPixel(
        runType: RunType,
        totalTimeInMillis: Long,
        totalParallelWebViews: Int,
    ) {
        if (runType == RunType.MANUAL) {
            pixelSender.reportManualScanCompleted(
                totalTimeInMillis = totalTimeInMillis,
                totalParallelWebViews = totalParallelWebViews,
                totalBrokerSuccess = repository.getScanSuccessResultsCount(),
                totalBrokerFailed = repository.getScanErrorResultsCount(),
            )
            repository.saveScanLog(
                PirEventLog(
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    eventType = EventType.MANUAL_SCAN_COMPLETED,
                ),
            )
        } else {
            pixelSender.reportScheduledScanCompleted(
                totalTimeInMillis = totalTimeInMillis,
                totalParallelWebViews = totalParallelWebViews,
                totalBrokerSuccess = repository.getScanSuccessResultsCount(),
                totalBrokerFailed = repository.getScanErrorResultsCount(),
            )
            repository.saveScanLog(
                PirEventLog(
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    eventType = EventType.SCHEDULED_SCAN_COMPLETED,
                ),
            )
        }
    }
}
