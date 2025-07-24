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

package com.duckduckgo.pir.internal.optout

import android.content.Context
import android.webkit.WebView
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.callbacks.PirCallbacks
import com.duckduckgo.pir.internal.common.BrokerStepsParser
import com.duckduckgo.pir.internal.common.PirActionsRunner
import com.duckduckgo.pir.internal.common.PirJob
import com.duckduckgo.pir.internal.common.PirJob.RunType.OPTOUT
import com.duckduckgo.pir.internal.common.PirJobConstants.MAX_DETACHED_WEBVIEW_COUNT
import com.duckduckgo.pir.internal.common.RealPirActionsRunner
import com.duckduckgo.pir.internal.common.splitIntoParts
import com.duckduckgo.pir.internal.models.Address
import com.duckduckgo.pir.internal.models.ProfileQuery
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

interface PirOptOut {
    /**
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
    private val brokerStepsParser: BrokerStepsParser,
    private val pirCssScriptLoader: PirCssScriptLoader,
    private val pirActionsRunnerFactory: RealPirActionsRunner.Factory,
    private val currentTimeProvider: CurrentTimeProvider,
    private val dispatcherProvider: DispatcherProvider,
    callbacks: PluginPoint<PirCallbacks>,
) : PirOptOut, PirJob(callbacks) {
    private var profileQueries: List<ProfileQuery> = listOf(
        ProfileQuery(
            id = -1,
            firstName = "William",
            lastName = "Smith",
            city = "Chicago",
            state = "IL",
            addresses = listOf(
                Address(
                    city = "Chicago",
                    state = "IL",
                ),
            ),
            birthYear = 1993,
            fullName = "William Smith",
            age = 32,
            deprecated = false,
        ),
        ProfileQuery(
            id = -2,
            firstName = "Jane",
            lastName = "Doe",
            city = "New York",
            state = "NY",
            addresses = listOf(
                Address(
                    city = "New York",
                    state = "NY",
                ),
            ),
            birthYear = 1990,
            fullName = "Jane Doe",
            age = 35,
            deprecated = false,
        ),
        ProfileQuery(
            id = -3,
            firstName = "Alicia",
            lastName = "West",
            city = "Los Angeles",
            state = "CA",
            addresses = listOf(
                Address(
                    city = "Los Angeles",
                    state = "CA",
                ),
            ),
            birthYear = 1985,
            fullName = "Alicia West",
            age = 40,
            deprecated = false,
        ),
    )

    private val runners: MutableList<PirActionsRunner> = mutableListOf()
    private var maxWebViewCount = 1

    override suspend fun debugExecute(
        brokers: List<String>,
        webView: WebView,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        onJobStarted()
        emitStartPixel()
        if (runners.isNotEmpty()) {
            cleanRunners()
            runners.clear()
        }
        obtainProfiles()

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
            runners.clear()
        }
        obtainProfiles()

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
        var createCount = 0
        while (createCount != maxWebViewCount) {
            runners.add(
                pirActionsRunnerFactory.create(
                    context,
                    script,
                    OPTOUT,
                ),
            )
            createCount++
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

    private suspend fun obtainProfiles() {
        repository.getUserProfileQueries().also { profiles ->
            if (profiles.isNotEmpty()) {
                profileQueries = profiles
            }
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
    }

    override fun stop() {
        logcat { "PIR-OPT-OUT: Stopping all runners" }
        cleanRunners()
        onJobStopped()
    }

    private suspend fun emitStartPixel() {
        repository.saveScanLog(
            PirEventLog(
                eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                eventType = EventType.MANUAL_OPTOUT_STARTED,
            ),
        )
    }

    private suspend fun emitCompletedPixel() {
        repository.saveScanLog(
            PirEventLog(
                eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                eventType = EventType.MANUAL_OPTOUT_COMPLETED,
            ),
        )
    }
}
