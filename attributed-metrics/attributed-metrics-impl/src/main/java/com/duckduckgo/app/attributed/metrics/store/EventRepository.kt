/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.attributed.metrics.store

import com.duckduckgo.app.attributed.metrics.api.EventStats
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface EventRepository {
    suspend fun collectEvent(eventName: String)

    suspend fun getEventStats(
        eventName: String,
        days: Int,
    ): EventStats

    suspend fun deleteOldEvents(olderThanDays: Int)
}

@ContributesBinding(AppScope::class)
class RealEventRepository @Inject constructor(
    private val eventDao: EventDao,
    private val attributedMetricsDateUtils: AttributedMetricsDateUtils,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : EventRepository {
    override suspend fun collectEvent(eventName: String) {
        val today = attributedMetricsDateUtils.getCurrentDate()
        val currentCount = eventDao.getEventCount(eventName, today)

        if (currentCount == null) {
            eventDao.insertEvent(EventEntity(eventName = eventName, count = 1, day = today))
        } else {
            eventDao.incrementEventCount(eventName, today)
        }
    }

    override suspend fun getEventStats(
        eventName: String,
        days: Int,
    ): EventStats {
        val startDay = attributedMetricsDateUtils.getDateMinusDays(days)

        val daysWithEvents = eventDao.getDaysWithEvents(eventName, startDay)
        val totalEvents = eventDao.getTotalEvents(eventName, startDay) ?: 0
        val rollingAverage = if (days > 0) totalEvents.toDouble() / days else 0.0

        return EventStats(
            daysWithEvents = daysWithEvents,
            rollingAverage = rollingAverage,
            totalEvents = totalEvents,
        )
    }

    override suspend fun deleteOldEvents(olderThanDays: Int) {
        coroutineScope.launch {
            val cutoffDay = attributedMetricsDateUtils.getDateMinusDays(olderThanDays)
            eventDao.deleteEventsOlderThan(cutoffDay)
        }
    }
}
