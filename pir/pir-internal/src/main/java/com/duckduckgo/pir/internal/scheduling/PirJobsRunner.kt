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

package com.duckduckgo.pir.internal.scheduling

import android.content.Context
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.PirInternalConstants.DEFAULT_PROFILE_QUERIES
import com.duckduckgo.pir.internal.common.PirJob.RunType
import com.duckduckgo.pir.internal.models.ProfileQuery
import com.duckduckgo.pir.internal.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.internal.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.internal.optout.PirOptOut
import com.duckduckgo.pir.internal.scan.PirScan
import com.duckduckgo.pir.internal.scheduling.PirExecutionType.MANUAL
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface PirJobsRunner {
    /**
     * This method is responsible for executing scans and opt-out jobs for any [ScanJobRecord] and/or [OptOutJobRecord] that is
     * eligible to be run at the current time.
     *
     * Note that any new [ScanJobRecord] and [OptOutJobRecord] are also created as part of the execution path.
     */
    suspend fun runEligibleJobs(
        context: Context,
        executionType: PirExecutionType,
    )

    /**
     * Stop any job that is in progress if any.
     */
    fun stop()
}

@ContributesBinding(scope = AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealPirJobsRunner @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val pirRepository: PirRepository,
    private val pirSchedulingRepository: PirSchedulingRepository,
    private val eligibleOptOutJobProvider: EligibleOptOutJobProvider,
    private val eligibleScanJobProvider: EligibleScanJobProvider,
    private val pirScan: PirScan,
    private val pirOptOut: PirOptOut,
    private val currentTimeProvider: CurrentTimeProvider,
) : PirJobsRunner {
    override suspend fun runEligibleJobs(
        context: Context,
        executionType: PirExecutionType,
    ) = withContext(dispatcherProvider.io()) {
        // This should be all brokers since all brokers have scan steps and inactive brokers are removed
        val activeBrokers = pirRepository.getAllBrokersForScan()

        // Multiple profile support
        val profileQueries = obtainProfiles()

        if (profileQueries.isEmpty()) return@withContext
        if (activeBrokers.isEmpty()) return@withContext

        attemptCreateScanJobs(activeBrokers, profileQueries)
        executeScanJobs(context, executionType)
        attemptCreateOptOutJobs(activeBrokers)
        executeOptOutJobs(context)
    }

    private suspend fun obtainProfiles(): List<ProfileQuery> {
        pirRepository.getUserProfileQueries().also { profiles ->
            return profiles.ifEmpty {
                DEFAULT_PROFILE_QUERIES
            }
        }
    }

    private suspend fun attemptCreateScanJobs(
        activeBrokers: List<String>,
        profileQueries: List<ProfileQuery>,
    ) {
        // No scan jobs mean that this is the first time PR is being run OR all jobs have been invalidated.
        val toCreate = mutableListOf<ScanJobRecord>()

        profileQueries.filter {
            !it.deprecated
        }.forEach { profile ->
            activeBrokers.forEach { broker ->
                if (pirSchedulingRepository.getValidScanJobRecord(broker, profile.id) == null) {
                    toCreate.add(
                        ScanJobRecord(
                            brokerName = broker,
                            userProfileId = profile.id,
                        ),
                    )
                }
            }
        }
        pirSchedulingRepository.saveScanJobRecords(toCreate)
    }

    private suspend fun executeScanJobs(
        context: Context,
        executionType: PirExecutionType,
    ) {
        eligibleScanJobProvider.getAllEligibleScanJobs(currentTimeProvider.currentTimeMillis())
            .also {
                val runType = if (executionType == MANUAL) {
                    RunType.MANUAL
                } else {
                    RunType.SCHEDULED
                }
                // pirScan.execute(it, context, runType)
            }
    }

    private suspend fun attemptCreateOptOutJobs(activeBrokers: List<String>) {
        val toCreate = mutableListOf<OptOutJobRecord>()
        val extractedProfiles = pirRepository.getAllExtractedProfiles()

        extractedProfiles.forEach {
            if (activeBrokers.contains(it.brokerName) &&
                pirSchedulingRepository.getValidOptOutJobRecord(it.dbId) != null
            ) {
                toCreate.add(
                    OptOutJobRecord(
                        extractedProfileId = it.dbId,
                        brokerName = it.brokerName,
                        userProfileId = it.profileQueryId,
                    ),
                )
            }
        }
        pirSchedulingRepository.saveOptOutJobRecords(toCreate)
    }

    private suspend fun executeOptOutJobs(context: Context) {
        eligibleOptOutJobProvider.getAllEligibleOptOutJobs(currentTimeProvider.currentTimeMillis())
            .also {
                // pirOptOut.execute(it, context)
            }
    }

    override fun stop() {
        pirScan.stop()
        pirOptOut.stop()
    }
}
