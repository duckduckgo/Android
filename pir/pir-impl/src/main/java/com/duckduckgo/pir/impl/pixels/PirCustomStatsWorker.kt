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

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.store.PirRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

@ContributesWorker(AppScope::class)
class PirCustomStatsWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    @Inject
    lateinit var optOutSubmitRateCalculator: OptOutSubmitRateCalculator

    @Inject
    lateinit var pirRepository: PirRepository

    @Inject
    lateinit var currentTimeProvider: CurrentTimeProvider

    @Inject
    lateinit var pirPixelSender: PirPixelSender

    override suspend fun doWork(): Result {
        val startDate = pirRepository.getCustomStatsPixelsLastSentMs()
        val now = currentTimeProvider.currentTimeMillis()

        if (shouldFirePixel(startDate, now)) {
            val endDate = now - TimeUnit.HOURS.toMillis(24)
            val activeBrokers = pirRepository.getAllActiveBrokerObjects()
            val hasUserProfiles = pirRepository.getAllUserProfileQueries().isNotEmpty()

            if (activeBrokers.isNotEmpty() && hasUserProfiles) {
                activeBrokers.forEach {
                    val successRate = optOutSubmitRateCalculator.calculateOptOutSubmitRate(
                        it.name,
                        startDate,
                        endDate,
                    )

                    if (successRate != null) {
                        pirPixelSender.reportBrokerCustomStateOptOutSubmitRate(
                            brokerUrl = it.url,
                            optOutSuccessRate = successRate,
                        )
                    }
                }

                pirRepository.setCustomStatsPixelsLastSentMs(endDate)
            }
        }

        return Result.success()
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

    companion object {
        const val TAG_PIR_RECURRING_CUSTOM_STATS = "TAG_PIR_RECURRING_CUSTOM_STATS"
    }
}
