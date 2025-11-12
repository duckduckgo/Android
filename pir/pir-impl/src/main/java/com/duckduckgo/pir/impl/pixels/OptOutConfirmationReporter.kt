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
        withContext(dispatcherProvider.io()) {
            val activeBrokers = pirRepository.getAllActiveBrokerObjects().associateBy { it.name }
            val allValidRequestedOptOutJobs = pirSchedulingRepository.getAllValidOptOutJobRecords().filter {
                it.status == REQUESTED || it.status == REMOVED // TODO: Filter out removed by user
            }

            if (activeBrokers.isEmpty() || allValidRequestedOptOutJobs.isEmpty()) return@withContext

            allValidRequestedOptOutJobs.also {
                it.attemptFirePixelForConfirmationDay(
                    activeBrokers,
                    7,
                    { jobRecord -> jobRecord.confirmation7dayReportSentDateMs == 0L },
                    { brokerUrl -> pixelSender.reportBrokerOptOutConfirmed7Days(brokerUrl) },
                    { brokerUrl -> pixelSender.reportBrokerOptOutUnconfirmed7Days(brokerUrl) },
                    { jobRecord, now ->
                        pirSchedulingRepository.markOptOutDay7ConfirmationPixelSent(jobRecord.extractedProfileId, now)
                    },
                )

                it.attemptFirePixelForConfirmationDay(
                    activeBrokers,
                    14,
                    { jobRecord -> jobRecord.confirmation14dayReportSentDateMs == 0L },
                    { brokerUrl -> pixelSender.reportBrokerOptOutConfirmed14Days(brokerUrl) },
                    { brokerUrl -> pixelSender.reportBrokerOptOutUnconfirmed14Days(brokerUrl) },
                    { jobRecord, now ->
                        pirSchedulingRepository.markOptOutDay14ConfirmationPixelSent(jobRecord.extractedProfileId, now)
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
        val optOutsForSevenDayPixel = this.filter {
            it.daysPassedSinceSubmission(now, confirmationDay) && jobRecordFilter(it)
        }

        optOutsForSevenDayPixel.forEach { optOutJobRecord ->
            val brokerUrl = activeBrokers[optOutJobRecord.brokerName]?.url ?: return@forEach

            if (optOutJobRecord.status == REMOVED) {
                emitConfirmPixel(brokerUrl)
            } else {
                emitUnconfirmPixel(brokerUrl)
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
}
