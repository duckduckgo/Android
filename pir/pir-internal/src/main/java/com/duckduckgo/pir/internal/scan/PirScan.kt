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
import com.duckduckgo.pir.internal.callbacks.PirCallbacks
import com.duckduckgo.pir.internal.common.BrokerStepsParser
import com.duckduckgo.pir.internal.common.PirActionsRunner
import com.duckduckgo.pir.internal.common.PirJob
import com.duckduckgo.pir.internal.common.PirJob.RunType
import com.duckduckgo.pir.internal.common.PirJobConstants.MAX_DETACHED_WEBVIEW_COUNT
import com.duckduckgo.pir.internal.common.RealPirActionsRunner
import com.duckduckgo.pir.internal.common.splitIntoParts
import com.duckduckgo.pir.internal.pixels.PirPixelSender
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

/**
 * This class is the main entry point for any scan execution (manual or scheduled)
 */
interface PirScan {

    /**
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

    override suspend fun execute(
        brokers: List<String>,
        context: Context,
        runType: RunType,
    ): Result<Unit> = withContext(dispatcherProvider.io()) {
        onJobStarted()

        val startTimeMillis = currentTimeProvider.currentTimeMillis()

        emitScanStartPixel(runType)
        cleanPreviousRun()

        val script = pirCssScriptLoader.getScript()
        maxWebViewCount = if (brokers.size <= MAX_DETACHED_WEBVIEW_COUNT) {
            brokers.size
        } else {
            MAX_DETACHED_WEBVIEW_COUNT
        }
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

        // Start each runner on a subset of the broker steps.
        brokers.mapNotNull { broker ->
            repository.getBrokerScanSteps(broker)?.run {
                brokerStepsParser.parseStep(broker, this)
            }
        }.splitIntoParts(maxWebViewCount)
            .mapIndexed { index, part ->
                // We want to run the runners in parallel but wait for everything complete before we proceed
                async {
                    runners[index].start(profileQuery, part)
                    runners[index].stop()
                }
            }.awaitAll()

        logcat { "PIR-SCAN: Scan completed for all runners" }
        emitScanCompletedPixel(
            runType,
            currentTimeProvider.currentTimeMillis() - startTimeMillis,
            maxWebViewCount,
        )
        onJobCompleted()
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

        logcat { "PIR-SCAN: Running scan on profile: $profileQuery on ${Thread.currentThread().name}" }
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
