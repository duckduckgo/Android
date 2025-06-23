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
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        onJobStarted()
        emitStartPixel()
        if (runners.isNotEmpty()) {
            cleanRunners()
            runners.clear()
        }
        obtainProfile()

        logcat { "PIR-OPT-OUT: Running opt-out on profile: $profileQuery on ${Thread.currentThread().name}" }

        runners.add(
            pirActionsRunnerFactory.create(
                webView.context,
                pirCssScriptLoader.getScript(),
                OPTOUT,
            ),
        )

        // Start each runner on a subset of the broker steps

        brokers.mapNotNull { broker ->
            repository.getBrokerOptOutSteps(broker)?.run {
                brokerStepsParser.parseStep(broker, this)
            }
        }.filter {
            it.isNotEmpty()
        }.flatten()
            .also { list ->
                runners[0].startOn(webView, profileQuery, list)
                runners[0].stop()
            }

        logcat { "PIR-OPT-OUT: Optout completed for all runners" }
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
        obtainProfile()

        logcat { "PIR-OPT-OUT: Running opt-out on profile: $profileQuery on ${Thread.currentThread().name}" }

        val script = pirCssScriptLoader.getScript()

        val brokerSteps = brokers.mapNotNull { broker ->
            repository.getBrokerOptOutSteps(broker)?.run {
                brokerStepsParser.parseStep(broker, this)
            }
        }.filter {
            it.isNotEmpty()
        }.flatten()

        maxWebViewCount = if (brokerSteps.size <= MAX_DETACHED_WEBVIEW_COUNT) {
            brokerSteps.size
        } else {
            MAX_DETACHED_WEBVIEW_COUNT
        }

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

        // Start each runner on a subset of the broker steps
        brokerSteps.splitIntoParts(maxWebViewCount)
            .mapIndexed { index, part ->
                async {
                    runners[index].start(profileQuery, part)
                    runners[index].stop()
                }
            }.awaitAll()

        logcat { "PIR-OPT-OUT: Optout completed for all runners" }
        emitCompletedPixel()
        onJobCompleted()
        return@withContext Result.success(Unit)
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
