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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.common.BrokerStepsParser
import com.duckduckgo.pir.internal.common.PirActionsRunner
import com.duckduckgo.pir.internal.common.PirActionsRunnerFactory
import com.duckduckgo.pir.internal.common.PirActionsRunnerFactory.RunType.OPTOUT
import com.duckduckgo.pir.internal.common.getMaximumParallelRunners
import com.duckduckgo.pir.internal.common.splitIntoParts
import com.duckduckgo.pir.internal.scripts.PirCssScriptLoader
import com.duckduckgo.pir.internal.scripts.models.ProfileQuery
import com.duckduckgo.pir.internal.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import logcat.logcat

interface PirOptOut {
    suspend fun execute(
        brokers: List<String>,
        context: Context,
    ): Result<Unit>

    suspend fun executeForBrokersWithRecords(
        context: Context,
    ): Result<Unit>

    fun stop()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealPirOptOut @Inject constructor(
    private val repository: PirRepository,
    private val brokerStepsParser: BrokerStepsParser,
    private val pirCssScriptLoader: PirCssScriptLoader,
    private val pirActionsRunnerFactory: PirActionsRunnerFactory,
) : PirOptOut {
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
        runBlocking {
            if (runners.isNotEmpty()) {
                stop()
                runners.clear()
            }
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
                        }
                            ?: "${storedProfile.userName.firstName} ${storedProfile.userName.lastName}",
                        age = storedProfile.age,
                        deprecated = false,
                    )
                }
            }
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
                pirActionsRunnerFactory.getInstance(
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
            }.splitIntoParts(maxWebViewCount)
                .mapIndexed { index, part ->
                    async {
                        runners[index].start(profileQuery, part)
                    }
                }.awaitAll()

            logcat { "PIR-OPT-OUT: Optout completed for all runners" }
            Result.success(Unit)
        }
    }

    override suspend fun executeForBrokersWithRecords(context: Context): Result<Unit> {
        val brokers = repository.getBrokersForOptOut()
        return execute(brokers, context)
    }

    override fun stop() {
        logcat { "PIR-OPT-OUT: Stopping all runners" }
        runners.forEach {
            runBlocking { it.stop() }
        }
    }
}
