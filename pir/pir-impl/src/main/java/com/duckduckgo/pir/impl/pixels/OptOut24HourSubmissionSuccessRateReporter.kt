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

package com.duckduckgo.pir.impl.pixels

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

interface OptOut24HourSubmissionSuccessRateReporter {
    suspend fun attemptFirePixel()
}

@ContributesBinding(AppScope::class)
class RealOptOut24HourSubmissionSuccessRateReporter @Inject constructor(
    private val optOutSubmitRateCalculator: OptOutSubmitRateCalculator,
    private val pirRepository: PirRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pirPixelSender: PirPixelSender,
    private val pirSchedulingRepository: PirSchedulingRepository,
    private val dispatcherProvider: DispatcherProvider,
) : OptOut24HourSubmissionSuccessRateReporter {
    override suspend fun attemptFirePixel() {
        withContext(dispatcherProvider.io()) {
            logcat { "PIR-CUSTOM-STATS: Attempt to fire 24hour submission pixels" }
            val lastSentMs = pirRepository.getCustomStatsPixelsLastSentMs()
            val now = currentTimeProvider.currentTimeMillis()

            if (!shouldFirePixel(lastSentMs, now)) return@withContext
            logcat { "PIR-CUSTOM-STATS: Should fire pixel - 24hrs passed since last send" }
            // previous endDate was (lastSentMs - 24h) so that should be our new startDate
            val startDate = if (lastSentMs == 0L) 0L else lastSentMs - TimeUnit.HOURS.toMillis(24)
            val endDate = now - TimeUnit.HOURS.toMillis(24)
            val activeBrokers = pirRepository.getAllActiveBrokerObjects()
            val hasUserProfiles = pirRepository.getAllUserProfileQueries().isNotEmpty()
            val activeOptOutJobRecords = pirSchedulingRepository.getAllValidOptOutJobRecords()

            if (activeBrokers.isNotEmpty() && activeOptOutJobRecords.isNotEmpty() && hasUserProfiles) {
                activeBrokers.forEach { broker ->
                    val activeJobRecordsForBroker = activeOptOutJobRecords.filter { it.brokerName == broker.name }

                    if (activeJobRecordsForBroker.isEmpty()) return@forEach

                    val successRate = optOutSubmitRateCalculator.calculateOptOutSubmitRate(
                        activeJobRecordsForBroker,
                        startDate,
                        endDate,
                    )

                    logcat { "PIR-CUSTOM-STATS: 24hr submission ${broker.name} : $successRate" }
                    if (successRate != null) {
                        pirPixelSender.reportBrokerCustomStateOptOutSubmitRate(
                            brokerUrl = broker.url,
                            optOutSuccessRate = successRate,
                        )
                    }
                }

                logcat { "PIR-CUSTOM-STATS: Updating last send date to $now" }
                pirRepository.setCustomStatsPixelsLastSentMs(now)
            }
        }
    }

    private fun shouldFirePixel(
        startDate: Long,
        now: Long,
    ): Boolean {
        return if (startDate == 0L) {
            // IF first run, we emit the custom stats pixel
            true
        } else {
            // Else we check if at least 24 hours have passed since last emission
            val nowDiffFromStart = abs(now - startDate)
            nowDiffFromStart > TimeUnit.HOURS.toMillis(24)
        }
    }
}
