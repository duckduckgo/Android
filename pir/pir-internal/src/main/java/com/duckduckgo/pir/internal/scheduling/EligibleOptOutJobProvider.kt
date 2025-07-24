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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.models.scheduling.BrokerSchedulingConfig
import com.duckduckgo.pir.internal.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.internal.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface EligibleOptOutJobProvider {
    suspend fun getAllEligibleOptOutJobs(timeInMillis: Long): List<OptOutJobRecord>
}

@ContributesBinding(scope = AppScope::class)
class RealEligibleOptOutJobProvider @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val pirSchedulingRepository: PirSchedulingRepository,
    private val pirRepository: PirRepository,
) : EligibleOptOutJobProvider {

    override suspend fun getAllEligibleOptOutJobs(
        timeInMillis: Long,
    ): List<OptOutJobRecord> = withContext(dispatcherProvider.io()) {
        val schedulingConfigs = pirRepository.getAllBrokerSchedulingConfigs()

        pirSchedulingRepository.getAllValidOptOutJobRecords().filter { record ->
            val schedulingConfig =
                schedulingConfigs.find { config -> config.brokerName == record.brokerName }

            schedulingConfig != null && (
                record.isNotYetExecuted() ||
                    record.isErrorAndShouldBeRetriedNow(schedulingConfig, timeInMillis) ||
                    record.isRequestAndShouldReRequested(schedulingConfig, timeInMillis)
                )
        }.sortedBy { it.attemptCount }
    }

    private fun OptOutJobRecord.isNotYetExecuted(): Boolean =
        this.status == OptOutJobStatus.NOT_EXECUTED

    private fun OptOutJobRecord.isErrorAndShouldBeRetriedNow(
        schedulingConfig: BrokerSchedulingConfig,
        timeInMillis: Long,
    ): Boolean =
        this.status == OptOutJobStatus.ERROR && this.lastOptOutAttemptDateInMillis != 0L &&
            (this.lastOptOutAttemptDateInMillis + schedulingConfig.retryErrorInMillis) <= timeInMillis

    private fun OptOutJobRecord.isRequestAndShouldReRequested(
        schedulingConfig: BrokerSchedulingConfig,
        timeInMillis: Long,
    ): Boolean {
        val hasMaxNoAttempts = schedulingConfig.maxAttempts <= 0
        val attemptWithinLimit = this.attemptCount <= schedulingConfig.maxAttempts
        val scheduledRun = this.optOutRequestedDateInMillis + RE_REQUEST_INTERVAL
        val scheduledRunIsInThePast = scheduledRun <= timeInMillis

        return this.status == OptOutJobStatus.REQUESTED && this.optOutRequestedDateInMillis != 0L &&
            (hasMaxNoAttempts || attemptWithinLimit) && scheduledRunIsInThePast
    }

    companion object {
        private val RE_REQUEST_INTERVAL = TimeUnit.DAYS.toMillis(28)
    }
}
