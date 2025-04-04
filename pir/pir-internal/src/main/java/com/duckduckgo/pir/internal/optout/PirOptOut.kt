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
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.callbacks.PirCallbacks
import com.duckduckgo.pir.internal.common.BrokerStepsParser
import com.duckduckgo.pir.internal.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.internal.common.PirActionsRunner
import com.duckduckgo.pir.internal.common.PirActionsRunnerFactory
import com.duckduckgo.pir.internal.common.PirActionsRunnerFactory.RunType.OPTOUT
import com.duckduckgo.pir.internal.common.PirJob
import com.duckduckgo.pir.internal.common.getMaximumParallelRunners
import com.duckduckgo.pir.internal.common.splitIntoParts
import com.duckduckgo.pir.internal.scripts.PirCssScriptLoader
import com.duckduckgo.pir.internal.scripts.models.Address
import com.duckduckgo.pir.internal.scripts.models.ProfileQuery
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.db.EventType
import com.duckduckgo.pir.internal.store.db.PirEventLog
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import logcat.logcat

interface PirOptOut {
    /**
     * This method can be used to execute pir opt-out for a given list of [brokers] names.
     *
     * @param brokers List of broker names
     * @param context Context in which we want to create the detached WebView from
     */
    suspend fun execute(
        brokers: List<String>,
        context: Context,
        coroutineScope: CoroutineScope,
    ): Result<Unit>

    /**
     * This method can be used to execute pir opt out for all active brokers where the user's profile has been identified as a record.
     *
     * @param context Context in which we want to create the detached WebView from
     */
    suspend fun executeForBrokersWithRecords(
        context: Context,
        coroutineScope: CoroutineScope,
    ): Result<Unit>

    /**
     * This method should only be used if we want to debug opt-out and pass in a specific [webView] which will allow us to see the run in action.
     * Do not use this for non-debug scenarios.
     *
     * @param brokers - brokers in which we want to run the opt out flow
     * @param webView - attached/visible WebView in which we will run the opt-out flow.
     */
    suspend fun debugExecute(
        brokers: List<String>,
        webView: WebView,
        coroutineScope: CoroutineScope,
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
    private val pirActionsRunnerFactory: PirActionsRunnerFactory,
    private val currentTimeProvider: CurrentTimeProvider,
    callbacks: PluginPoint<PirCallbacks>,
) : PirOptOut, PirJob(callbacks) {
    private var profileQuery: ProfileQuery = ProfileQuery(
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
    )

    private val runners: MutableList<PirActionsRunner> = mutableListOf()
    private var maxWebViewCount = 1

    override suspend fun debugExecute(
        brokers: List<String>,
        webView: WebView,
        coroutineScope: CoroutineScope,
    ): Result<Unit> {
        onJobStarted(coroutineScope)
        emitStartPixel()
        if (runners.isNotEmpty()) {
            cleanRunners()
            runners.clear()
        }
        obtainProfile()

        logcat { "PIR-OPT-OUT: Running opt-out on profile: $profileQuery on ${Thread.currentThread().name}" }

        runners.add(
            pirActionsRunnerFactory.createInstance(
                webView.context,
                pirCssScriptLoader.getScript(),
                OPTOUT,
            ),
        )

        // Start each runner on a subset of the broker steps
        return runBlocking {
            brokers.mapNotNull { broker ->
                repository.getBrokerOptOutSteps(broker)?.run {
                    brokerStepsParser.parseStep(broker, this)
                }
            }.filter {
                (it as OptOutStep).profilesToOptOut.isNotEmpty()
            }.also { list ->
                runners[0].startOn(webView, profileQuery, list)
            }

            logcat { "PIR-OPT-OUT: Optout completed for all runners" }
            emitCompletedPixel()
            onJobCompleted()
            Result.success(Unit)
        }
    }

    override suspend fun execute(
        brokers: List<String>,
        context: Context,
        coroutineScope: CoroutineScope,
    ): Result<Unit> {
        onJobStarted(coroutineScope)
        emitStartPixel()
        runBlocking {
            if (runners.isNotEmpty()) {
                cleanRunners()
                runners.clear()
            }
            obtainProfile()
        }

        logcat { "PIR-OPT-OUT: Running opt-out on profile: $profileQuery on ${Thread.currentThread().name}" }

        val script = runBlocking {
            pirCssScriptLoader.getScript()
        }

        val coreCount = getMaximumParallelRunners()
        maxWebViewCount = if (brokers.size <= coreCount) {
            brokers.size
        } else {
            coreCount
        }
        logcat { "PIR-OPT-OUT: Attempting to create $maxWebViewCount parallel runners on ${Thread.currentThread().name}" }

        // Initiate runners
        var createCount = 0
        while (createCount != maxWebViewCount) {
            runners.add(
                pirActionsRunnerFactory.createInstance(
                    context,
                    script,
                    OPTOUT,
                ),
            )
            createCount++
        }

        // Start each runner on a subset of the broker steps
        return runBlocking {
            brokers.mapNotNull { broker ->
                repository.getBrokerOptOutSteps(broker)?.run {
                    brokerStepsParser.parseStep(broker, this)
                }
            }.filter {
                (it as OptOutStep).profilesToOptOut.isNotEmpty()
            }.splitIntoParts(maxWebViewCount)
                .mapIndexed { index, part ->
                    async {
                        runners[index].start(profileQuery, part)
                    }
                }.awaitAll()

            logcat { "PIR-OPT-OUT: Optout completed for all runners" }
            emitCompletedPixel()
            onJobCompleted()
            Result.success(Unit)
        }
    }

    private suspend fun obtainProfile() {
        repository.getUserProfiles().also {
            if (it.isNotEmpty()) {
                // Temporarily taking the first profile only for the PoC. In the reality, more than 1 should be allowed.
                val storedProfile = it[0]
                profileQuery = ProfileQuery(
                    firstName = storedProfile.userName.firstName,
                    lastName = storedProfile.userName.lastName,
                    city = storedProfile.addresses.city,
                    state = storedProfile.addresses.state,
                    addresses = listOf(
                        Address(
                            city = storedProfile.addresses.city,
                            state = storedProfile.addresses.state,
                        ),
                    ),
                    birthYear = storedProfile.birthYear,
                    fullName = storedProfile.userName.middleName?.run {
                        "${storedProfile.userName.firstName} $this ${storedProfile.userName.lastName}"
                    }
                        ?: "${storedProfile.userName.firstName} ${storedProfile.userName.lastName}",
                    age = LocalDate.now().year - storedProfile.birthYear,
                    deprecated = false,
                )
            }
        }
    }

    override suspend fun executeForBrokersWithRecords(
        context: Context,
        coroutineScope: CoroutineScope,
    ): Result<Unit> {
        val brokers = repository.getBrokersForOptOut(formOptOutOnly = true)
        return execute(brokers, context, coroutineScope)
    }

    private fun cleanRunners() {
        runners.forEach {
            runBlocking { it.stop() }
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
