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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus.REMOVED
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus.REQUESTED
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.round

interface OptOutSubmitRateCalculator {
    /**
     * Calculates the opt-out 24h submit rate for a given broker within the specified date range.
     *
     * @param brokerName name of the broker to calculate the opt-out submit rate for.
     * @param startDateMs The opt-out records to include should be created on or after this date. Default is 0L (epoch).
     * @param endDateMs tThe opt-out records to include should be created on or before this date. Default is 0L (epoch).
     */
    suspend fun calculateOptOutSubmitRate(
        brokerName: String,
        startDateMs: Long = 0L,
        endDateMs: Long,
    ): Double?
}

@ContributesBinding(AppScope::class)
class RealOptOutSubmitRateCalculator @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val schedulingRepository: PirSchedulingRepository,
) : OptOutSubmitRateCalculator {
    override suspend fun calculateOptOutSubmitRate(
        brokerName: String,
        startDateMs: Long,
        endDateMs: Long,
    ): Double? = withContext(dispatcherProvider.io()) {
        // Get all opt out job records created within the given range for the specified broker
        val recordsCreatedWithinRange = schedulingRepository.getAllValidOptOutJobRecordsForBroker(brokerName).filter {
            it.brokerName == brokerName && it.dateCreatedInMillis in startDateMs..endDateMs
        }

        // We don't need to calculate the rate if there are no records
        if (recordsCreatedWithinRange.isEmpty()) return@withContext null

        // Filter the records to only include those that were requested within 24 hours of creation
        val requestedRecordsWithinRange = recordsCreatedWithinRange.filter {
            (it.status == REQUESTED || it.status == REMOVED) && it.optOutRequestedDateInMillis > it.dateCreatedInMillis &&
                it.optOutRequestedDateInMillis <= it.dateCreatedInMillis + TimeUnit.HOURS.toMillis(
                    24,
                )
        }

        val optOutSuccessRate = requestedRecordsWithinRange.size.toDouble() / recordsCreatedWithinRange.size.toDouble()
        val roundedOptOutSuccessRate = round(optOutSuccessRate * 100) / 100
        return@withContext roundedOptOutSuccessRate
    }
}
