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

package com.duckduckgo.pir.impl.email

import android.content.Context
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.PirJob.RunType.EMAIL_CONFIRMATION
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.EmailData
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.scheduling.JobRecordUpdater
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirRepository.EmailConfirmationLinkFetchStatus
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

interface PirEmailConfirmationJobsRunner {
    suspend fun runEligibleJobs(context: Context): Result<Unit>

    /**
     * Stop any job that is in progress if any.
     */
    fun stop()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealPirEmailConfirmationJobsRunner @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val pirSchedulingRepository: PirSchedulingRepository,
    private val pirRepository: PirRepository,
    private val jobRecordUpdater: JobRecordUpdater,
    private val emailConfirmation: PirEmailConfirmation,
    private val pirPixelSender: PirPixelSender,
    private val currentTimeProvider: CurrentTimeProvider,
) : PirEmailConfirmationJobsRunner {
    override suspend fun runEligibleJobs(context: Context): Result<Unit> {
        logcat { "PIR-EMAIL-CONFIRMATION: Starting run." }
        val activeBrokersMap = pirRepository.getAllActiveBrokerObjects().associateBy { it.name }

        runEmailConfirmationFetch(activeBrokersMap)
        runEmailConfirmationJobs(context, activeBrokersMap)
        logcat { "PIR-EMAIL-CONFIRMATION: Completed run." }
        return Result.success(Unit)
    }

    override fun stop() {
        logcat { "PIR-EMAIL-CONFIRMATION: Stopping runner." }
        emailConfirmation.stop()
    }

    private suspend fun runEmailConfirmationFetch(activeBrokersMap: Map<String, Broker>) =
        withContext(dispatcherProvider.io()) {
            logcat { "PIR-EMAIL-CONFIRMATION: Attempting to run email confirmation link fetch" }
            val eligibleJobRecordsMap =
                pirSchedulingRepository
                    .getEmailConfirmationJobsWithNoLink()
                    .filter {
                        it.brokerName in activeBrokersMap.keys
                    }.associateBy { it.emailData.email }
            if (eligibleJobRecordsMap.isEmpty()) {
                logcat { "PIR-EMAIL-CONFIRMATION: No fetch to run" }
                return@withContext
            }

            logcat { "PIR-EMAIL-CONFIRMATION: Attempting to fetch ${eligibleJobRecordsMap.size} links" }
            val emailDataForDeletion = mutableListOf<EmailData>()

            eligibleJobRecordsMap.forEach {
                jobRecordUpdater.recordEmailConfirmationFetchAttempt(it.value.extractedProfileId)
            }

            pirRepository.getEmailConfirmationLinkStatus(eligibleJobRecordsMap.values.map { it.emailData }).forEach {
                val record = eligibleJobRecordsMap[it.key.email]
                if (record == null) {
                    logcat { "PIR-EMAIL-CONFIRMATION: No job record found for email ${it.key.email}, skipping" }
                    return@forEach
                }

                logcat { "PIR-EMAIL-CONFIRMATION: For ${record.emailData} result is ${it.value}" }
                when (val status = it.value) {
                    is EmailConfirmationLinkFetchStatus.Ready -> {
                        status.data[KEY_LINK]?.let { link ->
                            handleLinkReady(record.extractedProfileId, status.emailReceivedAtMs, link, activeBrokersMap[record.brokerName]!!)
                            emailDataForDeletion.add(it.key)
                        }
                    }

                    is EmailConfirmationLinkFetchStatus.Error -> {
                        handleLinkFetchFailed(record.extractedProfileId, status, activeBrokersMap[record.brokerName]!!)
                        emailDataForDeletion.add(it.key)
                    }

                    is EmailConfirmationLinkFetchStatus.Unknown -> {
                        handleLinkFetchFailed(record.extractedProfileId, status, activeBrokersMap[record.brokerName]!!)
                    }

                    is EmailConfirmationLinkFetchStatus.Pending -> {
                        // no-op, still pending
                    }
                }
            }
            attemptDeleteEmailData(emailDataForDeletion)
        }

    private suspend fun handleLinkReady(
        extractedProfileId: Long,
        emailReceivedAtMs: Long,
        link: String,
        broker: Broker,
    ) {
        jobRecordUpdater.markEmailConfirmationWithLink(extractedProfileId, link)
        pirPixelSender.reportEmailConfirmationLinkFetched(
            brokerUrl = broker.url,
            brokerVersion = broker.version,
            linkAgeMs = currentTimeProvider.currentTimeMillis() - emailReceivedAtMs,
        )
    }

    private suspend fun handleLinkFetchFailed(
        extractedProfileId: Long,
        status: EmailConfirmationLinkFetchStatus,
        broker: Broker,
    ) {
        jobRecordUpdater.markEmailConfirmationLinkFetchFailed(extractedProfileId)

        when (status) {
            is EmailConfirmationLinkFetchStatus.Error -> {
                pirPixelSender.reportEmailConfirmationLinkFetchBEError(
                    brokerUrl = broker.url,
                    brokerVersion = broker.version,
                    status = status.statusString,
                    errorCode = status.errorCode,
                )
            }

            is EmailConfirmationLinkFetchStatus.Unknown -> {
                pirPixelSender.reportEmailConfirmationLinkFetchBEError(
                    brokerUrl = broker.url,
                    brokerVersion = broker.version,
                    status = status.statusString,
                    errorCode = status.errorCode,
                )
            }

            else -> {
                // no-op
            }
        }
    }

    private suspend fun attemptDeleteEmailData(emailData: List<EmailData>) {
        logcat { "PIR-EMAIL-CONFIRMATION: Attempting to delete ${emailData.size} email data" }
        if (emailData.isNotEmpty()) {
            pirRepository.deleteEmailData(emailData)
            logcat { "PIR-EMAIL-CONFIRMATION: Deletion complete." }
        }
    }

    private suspend fun runEmailConfirmationJobs(
        context: Context,
        activeBrokersMap: Map<String, Broker>,
    ) {
        logcat { "PIR-EMAIL-CONFIRMATION: Attempting to run email confirmation jobs" }
        val jobsWithLink =
            pirSchedulingRepository.getEmailConfirmationJobsWithLink().filter {
                it.brokerName in activeBrokersMap.keys
            }

        if (jobsWithLink.isNotEmpty()) {
            handleRecordsWithMaxAttempt(activeBrokersMap, jobsWithLink)
            handleEligibleJobRecords(context, jobsWithLink)
        } else {
            logcat { "PIR-EMAIL-CONFIRMATION: No jobs to run." }
        }
    }

    private suspend fun handleRecordsWithMaxAttempt(
        activeBrokersMap: Map<String, Broker>,
        jobsWithLink: List<EmailConfirmationJobRecord>,
    ) {
        logcat { "PIR-EMAIL-CONFIRMATION: Attempting to cleanup records that maxed attempts" }
        val toDelete =
            jobsWithLink.filter {
                it.jobAttemptData.jobAttemptCount >= EMAIL_CONFIRMATION_JOB_MAX_ATTEMPTS
            }

        if (toDelete.isNotEmpty()) {
            logcat { "PIR-EMAIL-CONFIRMATION: Cleaning up ${toDelete.size} records that maxed attempts" }
            toDelete.forEach {
                val broker = activeBrokersMap[it.brokerName]!!
                pirPixelSender.reportEmailConfirmationAttemptRetriesExceeded(
                    brokerUrl = broker.url,
                    brokerVersion = broker.version,
                    actionId = it.jobAttemptData.lastJobAttemptActionId,
                    attemptId = it.emailData.attemptId,
                )

                jobRecordUpdater.recordEmailConfirmationAttemptMaxed(it.extractedProfileId)
            }
        } else {
            logcat { "PIR-EMAIL-CONFIRMATION: Nothing to clean" }
        }
    }

    private suspend fun handleEligibleJobRecords(
        context: Context,
        jobsWitLink: List<EmailConfirmationJobRecord>,
    ) {
        logcat { "PIR-EMAIL-CONFIRMATION: Attempting to run email confirmation jobs" }
        val eligibleJobs =
            jobsWitLink.filter {
                it.jobAttemptData.jobAttemptCount < EMAIL_CONFIRMATION_JOB_MAX_ATTEMPTS
            }

        if (eligibleJobs.isNotEmpty()) {
            logcat { "PIR-EMAIL-CONFIRMATION: Running ${eligibleJobs.size} email confirmation jobs" }
            emailConfirmation.executeForEmailConfirmationJobs(
                eligibleJobs,
                context,
                EMAIL_CONFIRMATION,
            )
        } else {
            logcat { "PIR-EMAIL-CONFIRMATION: No email confirmation jobs to run." }
        }
    }

    companion object {
        private const val EMAIL_CONFIRMATION_JOB_MAX_ATTEMPTS = 3
        private const val KEY_LINK = "link"
    }
}
