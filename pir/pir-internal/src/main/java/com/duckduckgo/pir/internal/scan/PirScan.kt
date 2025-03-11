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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.component.PirActionsRunner
import com.duckduckgo.pir.internal.component.PirActionsRunnerFactory
import com.duckduckgo.pir.internal.scripts.PirCssScriptLoader
import com.duckduckgo.pir.internal.scripts.models.ProfileQuery
import com.duckduckgo.pir.internal.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import logcat.logcat

/**
 * This class is the main entry point for any scan execution (manual or scheduled)
 */
interface PirScan {

    /**
     * This method can be used to execute pir scan for a given list of [brokers] names.
     *
     * @param brokers List of broker names
     * @param context Context in which we want to create the detached WebView from
     */
    suspend fun execute(
        brokers: List<String>,
        context: Context,
    ): Result<Unit>

    /**
     * This method can be used to execute pir scan for all active brokers (from json).
     *
     * @param context Context in which we want to create the detached WebView from
     */
    suspend fun executeAllBrokers(
        context: Context,
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
    private val pirActionsRunnerFactory: PirActionsRunnerFactory,
) : PirScan {

    private var profileQuery: ProfileQuery = ProfileQuery(
        firstName = "William",
        lastName = "Smith",
        city = "Chicago",
        state = "IL",
        addresses = listOf(),
        birthYear = 1993,
        fullName = "William Smith",
        age = 34,
        deprecated = false,
    )

    private val runners: MutableList<PirActionsRunner> = mutableListOf()
    private var maxWebViewCount = 1

    override suspend fun execute(
        brokers: List<String>,
        context: Context,
    ): Result<Unit> {
        // Clean up previous run's results
        runBlocking {
            if (runners.isNotEmpty()) {
                stop()
                runners.clear()
            }
            repository.deleteAllResults()
            repository.getUserProfiles().also {
                if (it.isNotEmpty()) {
                    // Temporarily taking the first profile only for the PoC. In the reality, more than 1 should be allowed.
                    val storedProfile = it[0]
                    profileQuery = ProfileQuery(
                        firstName = storedProfile.userName.firstName,
                        lastName = storedProfile.userName.lastName,
                        city = storedProfile.addresses.city,
                        state = storedProfile.addresses.state,
                        addresses = listOf(),
                        birthYear = storedProfile.birthYear,
                        fullName = storedProfile.userName.middleName?.run {
                            "${storedProfile.userName.firstName} $this ${storedProfile.userName.lastName}"
                        } ?: "${storedProfile.userName.firstName} ${storedProfile.userName.lastName}",
                        age = storedProfile.age,
                        deprecated = false,
                    )
                }
            }
        }
        logcat { "PIR-SCAN: Running scan on profile: $profileQuery on ${Thread.currentThread().name}" }

        val script = runBlocking {
            pirCssScriptLoader.getScript()
        }

        maxWebViewCount = getMaximumParallelRunners()
        logcat { "PIR-SCAN: Attempting to create $maxWebViewCount parallel runners on ${Thread.currentThread().name}" }

        // Initiate runners
        var createCount = 0
        while (createCount != maxWebViewCount) {
            runners.add(
                pirActionsRunnerFactory.getInstance(
                    context,
                    script,
                ),
            )
            createCount++
        }

        // Start each runner on a subset of the broker steps
        return runBlocking {
            brokers.mapNotNull { broker ->
                repository.getBrokerScanSteps(broker)?.run {
                    brokerStepsParser.parseStep(broker, this)
                }
            }.splitIntoParts(maxWebViewCount)
                .mapIndexed { index, part ->
                    async {
                        runners[index].start(profileQuery, part)
                    }
                }.awaitAll()

            logcat { "PIR-SCAN: Scan completed for all runners" }
            Result.success(Unit)
        }
    }

    private fun getMaximumParallelRunners(): Int {
        return try {
            // Get the directory containing CPU info
            val cpuDir = File("/sys/devices/system/cpu/")
            // Filter folders matching the pattern "cpu[0-9]+"
            val cpuFiles = cpuDir.listFiles { file -> file.name.matches(Regex("cpu[0-9]+")) }
            cpuFiles?.size ?: Runtime.getRuntime().availableProcessors()
        } catch (e: Exception) {
            // In case of an error, fall back to availableProcessors
            Runtime.getRuntime().availableProcessors()
        }
    }

    private fun <T> List<T>.splitIntoParts(parts: Int): List<List<T>> {
        val chunkSize = (this.size + parts - 1) / parts // Ensure rounding up
        return this.chunked(chunkSize)
    }

    override suspend fun executeAllBrokers(
        context: Context,
    ): Result<Unit> {
        val brokers = runBlocking {
            repository.getAllBrokersForScan()
        }
        return execute(brokers, context)
    }

    override fun stop() {
        logcat { "PIR-SCAN: Stopping all runners" }
        runners.forEach {
            runBlocking { it.stop() }
        }
    }
}
