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

package com.duckduckgo.pir.impl.optout

import android.content.Context
import android.webkit.WebView
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirConstants
import com.duckduckgo.pir.impl.callbacks.PirCallbacks
import com.duckduckgo.pir.impl.common.BrokerStepsParser
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.PirActionsRunner
import com.duckduckgo.pir.impl.common.PirJob
import com.duckduckgo.pir.impl.common.PirJob.RunType.OPTOUT
import com.duckduckgo.pir.impl.common.PirJobConstants.MAX_DETACHED_WEBVIEW_COUNT
import com.duckduckgo.pir.impl.common.RealPirActionsRunner
import com.duckduckgo.pir.impl.common.splitIntoParts
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
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

interface PirOptOut {
    suspend fun executeOptOutForJobs(
        jobRecords: List<OptOutJobRecord>,
        context: Context,
    ): Result<Unit>

    /**
     * NOTE: This method should only be used for internal dev functionality only.
     * This method can be used to execute pir opt-out for a given list of [brokers] names.
     * You DO NOT need to set any dispatcher to call this suspend function. It is already set to run on IO.
     *
     * @param brokers List of broker names
     * @param context Context in which we want to create the detached WebView from
     */
    suspend fun execute(
        brokers: List<String>,
        context: Context,
    ): Result<Unit>

    /**
     * NOTE: This method should only be used for internal dev functionality only.
     * This method can be used to execute pir opt out for all active brokers where the user's profile has been identified as a record.
     * You DO NOT need to set any dispatcher to call this suspend function. It is already set to run on IO.
     *
     * @param context Context in which we want to create the detached WebView from
     */
    suspend fun executeForBrokersWithRecords(
        context: Context,
    ): Result<Unit>

    /**
     * This method should only be used if we want to debug opt-out and pass in a specific [webView] which will allow us to see the run in action.
     * Do not use this for non-debug scenarios.
     * You DO NOT need to set any dispatcher to call this suspend function. It is already set to run on IO.
     *
     * @param brokers - brokers in which we want to run the opt out flow
     * @param webView - attached/visible WebView in which we will run the opt-out flow.
     */
    suspend fun debugExecute(
        brokers: List<String>,
        webView: WebView,
    ): Result<Unit>

    /**
     * This method takes care of stopping the scan and cleaning up resources used.
     */
    fun stop()
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = PirOptOut::class,
)
@SingleInstanceIn(AppScope::class)
class RealPirOptOut @Inject constructor(
    private val repository: PirRepository,
    private val eventsRepository: PirEventsRepository,
    private val brokerStepsParser: BrokerStepsParser,
    private val pirCssScriptLoader: PirCssScriptLoader,
    private val pirActionsRunnerFactory: RealPirActionsRunner.Factory,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatcherProvider: DispatcherProvider,
    callbacks: PluginPoint<PirCallbacks>,
) : PirOptOut, PirJob(callbacks) {
    private val runners: MutableList<PirActionsRunner> = mutableListOf()
    private var maxWebViewCount = 1

    override suspend fun executeOptOutForJobs(
        jobRecords: List<OptOutJobRecord>,
        context: Context,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        logcat { "PIR-OPT-OUT: Running opt-out on the following records [${jobRecords.size}]: $jobRecords on ${Thread.currentThread().name}" }
        onJobStarted()
        emitStartPixel()

        if (jobRecords.isEmpty()) {
            logcat { "PIR-OPT-OUT: Nothing to opt-out." }
            completeOptOut()
            return@withContext Result.success(Unit)
        }

        if (runners.isNotEmpty()) {
            cleanRunners()
        }

        val processedJobRecords = processJobRecords(jobRecords)

        if (processedJobRecords.isEmpty()) {
            logcat { "PIR-OPT-OUT: No valid records. Nothing to opt-out." }
            completeOptOut()
            return@withContext Result.success(Unit)
        }

        val script = pirCssScriptLoader.getScript()
        maxWebViewCount = minOf(processedJobRecords.size, MAX_DETACHED_WEBVIEW_COUNT)

        logcat { "PIR-OPT-OUT: Attempting to create $maxWebViewCount parallel runners on ${Thread.currentThread().name}" }

        // Initiate runners
        repeat(maxWebViewCount) {
            runners.add(pirActionsRunnerFactory.create(context, script, OPTOUT))
        }

        val jobRecordsParts = processedJobRecords.splitIntoParts(maxWebViewCount)

        logcat { "PIR-OPT-OUT: Total parts ${jobRecordsParts.size}" }

        jobRecordsParts.mapIndexed { index, partSteps ->
            logcat { "PIR-OPT-OUT: Record part [$index] -> ${partSteps.size}" }
            async {
                partSteps.map { (profileQuery, step) ->
                    logcat {
                        "PIR-OPT-OUT: Start opt-out on runner=$index, extractedProfile=${(step as OptOutStep).profileToOptOut.dbId} " +
                            "broker=${step.brokerName} profile=${profileQuery.id}"
                    }
                    runners[index].start(profileQuery, listOf(step))
                    runners[index].stop()
                    logcat {
                        "PIR-OPT-OUT: Finish opt-out on runner=$index, extractedProfile=${(step as OptOutStep).profileToOptOut.dbId} " +
                            "broker=${step.brokerName} profile=${profileQuery.id}"
                    }
                }
            }
        }.awaitAll()

        completeOptOut()
        return@withContext Result.success(Unit)
    }

    private suspend fun processJobRecords(jobRecords: List<OptOutJobRecord>): List<Pair<ProfileQuery, BrokerStep>> {
        val allUserProfiles = obtainProfiles().associateBy { it.id }
        if (allUserProfiles.isEmpty()) {
            logcat { "PIR-OPT-OUT: No valid user profile available. Nothing to opt-out." }
            return emptyList()
        }

        // Load opt-out steps jsons for each broker
        val brokerOptOutStepsJsons = jobRecords.map { it.brokerName }.distinct().mapNotNull { broker ->
            repository.getBrokerOptOutSteps(broker)?.let { broker to it }
        }.toMap()

        if (brokerOptOutStepsJsons.isEmpty()) {
            logcat { "PIR-OPT-OUT: No valid broker's with opt-out steps. Nothing to opt-out." }
            return emptyList()
        }

        val temporaryCache = mutableMapOf<String, List<BrokerStep>>()

        val processedJobRecords = jobRecords.mapNotNull { record ->
            var brokerStep: BrokerStep? = null

            val profileQuery = allUserProfiles[record.userProfileId]
            if (profileQuery == null) {
                logcat { "PIR-OPT-OUT: No profile query found for userProfileId=${record.userProfileId}. Skipping opt-out." }
                return@mapNotNull null
            }

            val temporaryCacheKey = record.brokerName + record.userProfileId
            if (temporaryCache[temporaryCacheKey] != null) {
                // Find extractedProfile from cached BrokerSteps list
                brokerStep = temporaryCache[temporaryCacheKey]?.find { (it as OptOutStep).profileToOptOut.dbId == record.extractedProfileId }
            } else {
                // Parse broker steps - this will return the extractedProfiles too
                val brokerSteps = brokerOptOutStepsJsons[record.brokerName]?.let { stepsJson ->
                    brokerStepsParser.parseStep(
                        record.brokerName,
                        stepsJson,
                        record.userProfileId,
                    )
                }

                if (!brokerSteps.isNullOrEmpty()) {
                    // Store the brokers steps to cache, find the extractedProfile from the brokerSteps
                    temporaryCache[record.brokerName + record.userProfileId] = brokerSteps
                    brokerStep = brokerSteps.find { (it as OptOutStep).profileToOptOut.dbId == record.extractedProfileId }
                }
            }

            if (brokerStep == null) {
                logcat { "PIR-OPT-OUT: Extracted Profile not found. Skipping opt-out." }
                return@mapNotNull null
            } else {
                return@mapNotNull profileQuery to brokerStep
            }
        }

        temporaryCache.clear()
        logcat { "PIR-OPT-OUT: Total processed records ${processedJobRecords.size}" }

        if (processedJobRecords.isEmpty()) {
            logcat { "PIR-OPT-OUT: No valid records. Nothing to opt-out." }
            return emptyList()
        }
        return processedJobRecords
    }

    private suspend fun completeOptOut() {
        logcat { "PIR-OPT-OUT: Opt-out completed for all runners and profiles" }
        emitCompletedPixel()
        onJobCompleted()
    }

    override suspend fun debugExecute(
        brokers: List<String>,
        webView: WebView,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        onJobStarted()
        emitStartPixel()
        if (runners.isNotEmpty()) {
            cleanRunners()
        }
        val profileQueries = obtainProfiles()

        logcat { "PIR-OPT-OUT: Running debug opt-out for $brokers on profiles: $profileQueries on ${Thread.currentThread().name}" }

        runners.add(
            pirActionsRunnerFactory.create(
                webView.context,
                pirCssScriptLoader.getScript(),
                OPTOUT,
            ),
        )

        // Load opt-out steps jsons for each broker
        val brokerOptOutStepsJsons = brokers.mapNotNull { broker ->
            repository.getBrokerOptOutSteps(broker)?.let { broker to it }
        }

        // Map broker steps with their associated profile queries
        val allSteps = profileQueries.map { profileQuery ->
            brokerOptOutStepsJsons.map { (broker, stepsJson) ->
                brokerStepsParser.parseStep(broker, stepsJson, profileQuery.id)
            }.flatten().map { step -> profileQuery to step }
        }.flatten()

        // Execute each steps sequentially on the single runner
        allSteps.forEach { (profileQuery, step) ->
            logcat { "PIR-OPT-OUT: Start thread=${Thread.currentThread().name}, profile=$profileQuery and step=$step" }
            runners[0].startOn(webView, profileQuery, listOf(step))
            runners[0].stop()
            logcat { "PIR-OPT-OUT: Finish thread=${Thread.currentThread().name}, profile=$profileQuery and step=$step" }
        }

        logcat { "PIR-OPT-OUT: Opt-out completed for all runners and profiles" }

        emitCompletedPixel()
        onJobCompleted()
        return@withContext Result.success(Unit)
    }

    override suspend fun execute(
        brokers: List<String>,
        context: Context,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        onJobStarted()
        emitStartPixel()
        if (runners.isNotEmpty()) {
            cleanRunners()
        }
        val profileQueries = obtainProfiles()

        logcat { "PIR-OPT-OUT: Running opt-out on profiles: $profileQueries on ${Thread.currentThread().name}" }

        val script = pirCssScriptLoader.getScript()

        // Load opt-out steps jsons for each broker
        val brokerOptOutStepsJsons = brokers.mapNotNull { broker ->
            repository.getBrokerOptOutSteps(broker)?.let { broker to it }
        }

        // Map broker steps with their associated profile queries
        val allSteps = profileQueries.map { profileQuery ->
            brokerOptOutStepsJsons.map { (broker, stepsJson) ->
                brokerStepsParser.parseStep(broker, stepsJson, profileQuery.id)
            }.flatten().map { step -> profileQuery to step }
        }.flatten()

        maxWebViewCount = minOf(allSteps.size, MAX_DETACHED_WEBVIEW_COUNT)

        // Assign steps to runners based on the maximum number of WebViews we can use
        val stepsPerRunner = allSteps.splitIntoParts(maxWebViewCount)

        logcat { "PIR-OPT-OUT: Attempting to create $maxWebViewCount parallel runners on ${Thread.currentThread().name}" }

        // Initiate runners
        repeat(maxWebViewCount) {
            runners.add(pirActionsRunnerFactory.create(context, script, OPTOUT))
        }

        // Execute the steps on all runners in parallel
        stepsPerRunner.mapIndexed { index, partSteps ->
            async {
                partSteps.map { (profileQuery, step) ->
                    logcat { "PIR-OPT-OUT: Start opt-out on runner=$index, profile=$profileQuery and step=$step" }
                    runners[index].start(profileQuery, listOf(step))
                    runners[index].stop()
                    logcat { "PIR-OPT-OUT: Finish opt-out on runner=$index, profile=$profileQuery and step=$step" }
                }
            }
        }.awaitAll()

        logcat { "PIR-OPT-OUT: Opt-out completed for all runners and profiles" }
        emitCompletedPixel()
        onJobCompleted()
        return@withContext Result.success(Unit)
    }

    private suspend fun obtainProfiles(): List<ProfileQuery> {
        return repository.getUserProfileQueries().ifEmpty {
            PirConstants.DEFAULT_PROFILE_QUERIES
        }
    }

    override suspend fun executeForBrokersWithRecords(
        context: Context,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        val brokers = repository.getBrokersForOptOut(formOptOutOnly = true)
        return@withContext execute(brokers, context)
    }

    private fun cleanRunners() {
        runners.forEach {
            it.stop()
        }
        runners.clear()
    }

    override fun stop() {
        logcat { "PIR-OPT-OUT: Stopping all runners" }
        cleanRunners()
        onJobStopped()
    }

    private suspend fun emitStartPixel() {
        eventsRepository.saveEventLog(
            PirEventLog(
                eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                eventType = EventType.MANUAL_OPTOUT_STARTED,
            ),
        )
    }

    private suspend fun emitCompletedPixel() {
        eventsRepository.saveEventLog(
            PirEventLog(
                eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                eventType = EventType.MANUAL_OPTOUT_COMPLETED,
            ),
        )
    }
}
