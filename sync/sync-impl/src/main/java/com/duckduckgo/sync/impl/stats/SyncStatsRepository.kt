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

import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.sync.impl.engine.AppSyncStateRepository
import com.duckduckgo.sync.impl.engine.SyncStateRepository
import com.duckduckgo.sync.store.dao.SyncAttemptDao
import com.duckduckgo.sync.store.model.SyncAttempt
import com.duckduckgo.sync.store.model.SyncAttemptState.FAIL
import com.duckduckgo.sync.store.model.SyncAttemptState.SUCCESS
import org.threeten.bp.LocalDate
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneOffset
import java.math.RoundingMode
import java.text.DecimalFormat
import javax.inject.Inject

interface SyncStatsRepository {

    fun getDailyStats(): DailyStats
}

data class DailyStats(
    val attempts: Int,
    val successRate: Double
)

class RealSyncStatsRepository @Inject constructor(private val syncStateRepository: SyncStateRepository) : SyncStatsRepository {
    override fun getDailyStats(): DailyStats {
        val attempts = syncStateRepository.attempts().filter {
            it.today()
        }
        val successfulAttempts = attempts.filter { it.state == SUCCESS }.size
        val totalAttempts = attempts.size

        val successRate = if (totalAttempts > 0) {
            successfulAttempts.toDouble() / totalAttempts.toDouble() * 100
        } else {
            0.0
        }

        val roundedUp = successRate.toBigDecimal().setScale(2, RoundingMode.UP).toDouble()
        return DailyStats(totalAttempts, roundedUp)
    }
}
