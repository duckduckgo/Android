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

package com.duckduckgo.pir.impl.scheduling

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.models.scheduling.BrokerSchedulingConfig
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface EligibleScanJobProvider {
    suspend fun getAllEligibleScanJobs(timeInMillis: Long): List<ScanJobRecord>
}

@ContributesBinding(scope = AppScope::class)
class RealEligibleScanJobProvider @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val pirSchedulingRepository: PirSchedulingRepository,
    private val pirRepository: PirRepository,
) : EligibleScanJobProvider {
    override suspend fun getAllEligibleScanJobs(timeInMillis: Long): List<ScanJobRecord> =
        withContext(dispatcherProvider.io()) {
            val schedulingConfigs = pirRepository.getAllBrokerSchedulingConfigs()
            val validScanJobsFromOptOut = getValidScanJobsFromOptOut(
                schedulingConfigs,
                timeInMillis,
            )
            val validScanJobsFromScanRecords = getValidScanJobsFromScanJobRecords(
                schedulingConfigs,
                timeInMillis,
            )

            return@withContext (validScanJobsFromOptOut + validScanJobsFromScanRecords)
                .toSet()
                .toList()
        }

    private suspend fun getValidScanJobsFromOptOut(
        schedulingConfigs: List<BrokerSchedulingConfig>,
        timeInMillis: Long,
    ): List<ScanJobRecord> {
        return pirSchedulingRepository.getAllValidOptOutJobRecords().filter { record ->
            val schedulingConfig =
                schedulingConfigs.find { config -> config.brokerName == record.brokerName }

            schedulingConfig != null && (
                record.isRequestedAndShouldBeConfirmedNow(schedulingConfig, timeInMillis) ||
                    record.isRemovedAndShouldBeMaintainedNow(schedulingConfig, timeInMillis)
                )
        }.sortedBy {
            it.attemptCount
        }.mapNotNull {
            pirSchedulingRepository.getValidScanJobRecord(it.brokerName, it.userProfileId)
        }
    }

    private fun OptOutJobRecord.isRequestedAndShouldBeConfirmedNow(
        schedulingConfig: BrokerSchedulingConfig,
        timeInMillis: Long,
    ): Boolean {
        return this.status == OptOutJobStatus.REQUESTED &&
            (this.optOutRequestedDateInMillis + schedulingConfig.confirmOptOutScanInMillis) <= timeInMillis
    }

    private fun OptOutJobRecord.isRemovedAndShouldBeMaintainedNow(
        schedulingConfig: BrokerSchedulingConfig,
        timeInMillis: Long,
    ): Boolean {
        return this.status == OptOutJobStatus.REMOVED &&
            (this.optOutRemovedDateInMillis + schedulingConfig.maintenanceScanInMillis) <= timeInMillis
    }

    private suspend fun getValidScanJobsFromScanJobRecords(
        schedulingConfigs: List<BrokerSchedulingConfig>,
        timeInMillis: Long,
    ): List<ScanJobRecord> {
        return pirSchedulingRepository.getAllValidScanJobRecords().filter { record ->
            val schedulingConfig =
                schedulingConfigs.find { config -> config.brokerName == record.brokerName }

            schedulingConfig != null && (
                record.isNotYetExecuted() ||
                    record.isNoMatchAndShouldBeMaintained(schedulingConfig, timeInMillis) ||
                    record.hasMatchAndShouldBeMaintained(schedulingConfig, timeInMillis) ||
                    record.isErrorAndShouldBeRetried(schedulingConfig, timeInMillis)
                )
        }.sortedBy {
            it.lastScanDateInMillis
        }
    }

    private fun ScanJobRecord.isNotYetExecuted(): Boolean {
        return this.lastScanDateInMillis == 0L || this.status == ScanJobStatus.NOT_EXECUTED
    }

    private fun ScanJobRecord.isNoMatchAndShouldBeMaintained(
        schedulingConfig: BrokerSchedulingConfig,
        timeInMillis: Long,
    ): Boolean {
        return this.status == ScanJobStatus.NO_MATCH_FOUND && this.lastScanDateInMillis != 0L &&
            (this.lastScanDateInMillis + schedulingConfig.maintenanceScanInMillis) <= timeInMillis
    }

    private fun ScanJobRecord.hasMatchAndShouldBeMaintained(
        schedulingConfig: BrokerSchedulingConfig,
        timeInMillis: Long,
    ): Boolean {
        return this.status == ScanJobStatus.MATCHES_FOUND && this.lastScanDateInMillis != 0L &&
            (this.lastScanDateInMillis + schedulingConfig.maintenanceScanInMillis) <= timeInMillis
    }

    private fun ScanJobRecord.isErrorAndShouldBeRetried(
        schedulingConfig: BrokerSchedulingConfig,
        timeInMillis: Long,
    ): Boolean {
        return this.status == ScanJobStatus.ERROR && this.lastScanDateInMillis != 0L &&
            (this.lastScanDateInMillis + schedulingConfig.retryErrorInMillis) <= timeInMillis
    }
}
