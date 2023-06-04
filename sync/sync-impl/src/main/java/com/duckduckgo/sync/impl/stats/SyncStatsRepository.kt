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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.engine.SyncStateRepository
import com.duckduckgo.sync.store.model.SyncState.SUCCESS
import com.squareup.anvil.annotations.ContributesBinding
import org.threeten.bp.OffsetDateTime
import javax.inject.Inject

interface SyncStatsRepository {

    fun getSyncStats(day: OffsetDateTime): SyncStats
}

@ContributesBinding(AppScope::class)
class AppSyncStatsRepository @Inject constructor(val syncStateRepository: SyncStateRepository) : SyncStatsRepository {
    override fun getSyncStats(day: OffsetDateTime): SyncStats {
        val attempts = syncStateRepository.attempts(day)
        val totalAttempts = attempts.size
        val success = attempts.filter { it.state == SUCCESS }.size.toFloat()
        val failures = attempts.filter { it.state != SUCCESS }.size.toFloat()
        return SyncStats(totalAttempts, success / totalAttempts.toFloat(), failures / totalAttempts.toFloat())
    }
}

data class SyncStats(
    val totalAttempts: Int,
    val successRate: Float,
    val failureRate: Float
)
