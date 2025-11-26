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
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus.REMOVED
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus.REQUESTED
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface OptOutConfirmationReporter {
    suspend fun attemptFirePixel()
}

@ContributesBinding(AppScope::class)
class RealOptOutConfirmationReporter @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val pirSchedulingRepository: PirSchedulingRepository,
    private val pirRepository: PirRepository,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pixelSender: PirPixelSender,
) : OptOutConfirmationReporter {
    override suspend fun attemptFirePixel() {
        logcat { "PIR-CUSTOM-STATS: Attempt to fire confirmation pixels" }

        withContext(dispatcherProvider.io()) {
            val activeBrokers = pirRepository.getAllActiveBrokerObjects().associateBy { it.name }
            val allValidRequestedOptOutJobs = pirSchedulingRepository.getAllValidOptOutJobRecords().filter {
                it.status == REQUESTED || it.status == REMOVED
            }

            if (activeBrokers.isEmpty() || allValidRequestedOptOutJobs.isEmpty()) return@withContext

            logcat { "PIR-CUSTOM-STATS: Will fire confirmation pixels for ${allValidRequestedOptOutJobs.size} jobs" }
            allValidRequestedOptOutJobs.also {
                // Fire 7 day pixel
                it.attemptFirePixelForConfirmationDay(
                    activeBrokers,
                    INTERVAL_DAY_7,
                    { jobRecord -> jobRecord.confirmation7dayReportSentDateMs == 0L },
                    { brokerUrl -> pixelSender.reportBrokerOptOutConfirmed7Days(brokerUrl) },
                    { brokerUrl -> pixelSender.reportBrokerOptOutUnconfirmed7Days(brokerUrl) },
                    { jobRecord, now ->
                        pirSchedulingRepository.markOptOutDay7ConfirmationPixelSent(jobRecord.extractedProfileId, now)
                    },
                )

                // Fire 14 day pixel
                it.attemptFirePixelForConfirmationDay(
                    activeBrokers,
                    INTERVAL_DAY_14,
                    { jobRecord -> jobRecord.confirmation14dayReportSentDateMs == 0L },
                    { brokerUrl -> pixelSender.reportBrokerOptOutConfirmed14Days(brokerUrl) },
                    { brokerUrl -> pixelSender.reportBrokerOptOutUnconfirmed14Days(brokerUrl) },
                    { jobRecord, now ->
                        pirSchedulingRepository.markOptOutDay14ConfirmationPixelSent(jobRecord.extractedProfileId, now)
                    },
                )

                // Fire 21 day pixel
                it.attemptFirePixelForConfirmationDay(
                    activeBrokers,
                    INTERVAL_DAY_21,
                    { jobRecord -> jobRecord.confirmation21dayReportSentDateMs == 0L },
                    { brokerUrl -> pixelSender.reportBrokerOptOutConfirmed21Days(brokerUrl) },
                    { brokerUrl -> pixelSender.reportBrokerOptOutUnconfirmed21Days(brokerUrl) },
                    { jobRecord, now ->
                        pirSchedulingRepository.markOptOutDay21ConfirmationPixelSent(jobRecord.extractedProfileId, now)
                    },
                )

                // Fire 42 day pixel
                it.attemptFirePixelForConfirmationDay(
                    activeBrokers,
                    INTERVAL_DAY_42,
                    { jobRecord -> jobRecord.confirmation42dayReportSentDateMs == 0L },
                    { brokerUrl -> pixelSender.reportBrokerOptOutConfirmed42Days(brokerUrl) },
                    { brokerUrl -> pixelSender.reportBrokerOptOutUnconfirmed42Days(brokerUrl) },
                    { jobRecord, now ->
                        pirSchedulingRepository.markOptOutDay42ConfirmationPixelSent(jobRecord.extractedProfileId, now)
                    },
                )
            }
        }
    }

    private suspend fun List<OptOutJobRecord>.attemptFirePixelForConfirmationDay(
        activeBrokers: Map<String, Broker>,
        confirmationDay: Long,
        jobRecordFilter: (OptOutJobRecord) -> Boolean,
        emitConfirmPixel: (String) -> Unit,
        emitUnconfirmPixel: (String) -> Unit,
        markOptOutJobRecordReporting: suspend (OptOutJobRecord, Long) -> Unit,
    ) {
        val now = currentTimeProvider.currentTimeMillis()
        val optOutsForPixel = this.filter {
            it.daysPassedSinceSubmission(now, confirmationDay) && jobRecordFilter(it)
        }

        logcat { "PIR-CUSTOM-STATS: Firing $confirmationDay day confirmation pixels for ${optOutsForPixel.size} jobs" }
        optOutsForPixel.forEach { optOutJobRecord ->
            val broker = activeBrokers[optOutJobRecord.brokerName] ?: return@forEach

            if (optOutJobRecord.status == REMOVED) {
                logcat { "PIR-CUSTOM-STATS: Firing $confirmationDay day confirmation pixels for ${broker.name}" }
                emitConfirmPixel(broker.url)
            } else {
                logcat { "PIR-CUSTOM-STATS: Firing $confirmationDay day unconfirmation pixels for ${broker.name}" }
                emitUnconfirmPixel(broker.url)
            }

            markOptOutJobRecordReporting(optOutJobRecord, now)
        }
    }

    private fun OptOutJobRecord.daysPassedSinceSubmission(
        now: Long,
        interval: Long,
    ): Boolean {
        return now >= this.optOutRequestedDateInMillis + TimeUnit.DAYS.toMillis(interval)
    }

    companion object {
        private const val INTERVAL_DAY_7 = 7L
        private const val INTERVAL_DAY_14 = 14L
        private const val INTERVAL_DAY_21 = 21L
        private const val INTERVAL_DAY_42 = 42L
    }
}
