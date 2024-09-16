/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.stats

import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.sync.impl.engine.SyncStateRepository
import com.duckduckgo.sync.impl.error.SyncApiErrorRepository
import com.duckduckgo.sync.impl.error.SyncOperationErrorRepository
import com.duckduckgo.sync.store.model.SyncAttempt
import javax.inject.Inject

/**
 * The Repository that fetches all stats related to [SyncAttempt]
 */
interface SyncStatsRepository {

    /**
     * Returns yesterday's [DailyStats]
     * @return [DailyStats]
     */
    fun getYesterdayDailyStats(): DailyStats
}

data class DailyStats(
    val attempts: String,
    val date: String,
    val apiErrorStats: Map<String, String> = emptyMap(),
    val operationErrorStats: Map<String, String> = emptyMap(),
)

class RealSyncStatsRepository @Inject constructor(
    private val syncStateRepository: SyncStateRepository,
    private val syncApiErrorRepository: SyncApiErrorRepository,
    private val syncOperationErrorRepository: SyncOperationErrorRepository,
) : SyncStatsRepository {
    override fun getYesterdayDailyStats(): DailyStats {
        val yesterday = DatabaseDateFormatter.getUtcIsoLocalDate(1)
        val count = syncStateRepository.attempts().filter {
            it.yesterday()
        }.size
        val apiErrorMap = syncApiErrorRepository.getErrorsByDate(yesterday).associate { it.name to it.count }
        val operationErrorMap = syncOperationErrorRepository.getErrorsByDate(yesterday).associate { it.name to it.count }
        return DailyStats(count.toString(), yesterday, apiErrorMap, operationErrorMap)
    }
}
