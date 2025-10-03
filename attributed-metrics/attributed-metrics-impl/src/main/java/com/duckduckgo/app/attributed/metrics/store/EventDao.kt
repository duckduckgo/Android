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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EventDao {
    @Query("SELECT * FROM event_metrics WHERE eventName = :eventName AND day >= :startDay AND day <= :endDay ORDER BY day DESC")
    suspend fun getEventsByNameAndTimeframe(
        eventName: String,
        startDay: String,
        endDay: String,
    ): List<EventEntity>

    @Query("SELECT COUNT(DISTINCT day) FROM event_metrics WHERE eventName = :eventName AND day >= :startDay AND day <= :endDay")
    suspend fun getDaysWithEvents(
        eventName: String,
        startDay: String,
        endDay: String,
    ): Int

    @Query("SELECT SUM(count) FROM event_metrics WHERE eventName = :eventName AND day >= :startDay AND day <= :endDay")
    suspend fun getTotalEvents(
        eventName: String,
        startDay: String,
        endDay: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Query(
        """
        UPDATE event_metrics
        SET count = count + 1
        WHERE eventName = :eventName AND day = :day
    """,
    )
    suspend fun incrementEventCount(
        eventName: String,
        day: String,
    )

    @Query("SELECT count FROM event_metrics WHERE eventName = :eventName AND day = :day")
    suspend fun getEventCount(
        eventName: String,
        day: String,
    ): Int?

    @Query("DELETE FROM event_metrics WHERE day < :day")
    suspend fun deleteEventsOlderThan(day: String)
}
