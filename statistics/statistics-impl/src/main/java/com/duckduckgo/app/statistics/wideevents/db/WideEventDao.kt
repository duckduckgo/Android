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

package com.duckduckgo.app.statistics.wideevents.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WideEventDao {
    @Insert
    suspend fun insertWideEvent(event: WideEventEntity): Long

    @Query("SELECT * FROM wide_events WHERE id = :id")
    suspend fun getWideEventById(id: Long): WideEventEntity?

    @Query("SELECT * FROM wide_events WHERE id IN (:ids)")
    suspend fun getWideEventsByIds(ids: Set<Long>): List<WideEventEntity>

    @Query("SELECT id FROM wide_events WHERE status is null ORDER BY id ASC")
    suspend fun getActiveWideEventIds(): List<Long>

    @Query("SELECT id FROM wide_events WHERE name = :name AND status is null ORDER BY id ASC")
    suspend fun getActiveWideEventIdsByName(name: String): List<Long>

    @Update
    suspend fun updateWideEvent(event: WideEventEntity): Int

    @Query("DELETE FROM wide_events WHERE id = :id")
    suspend fun deleteWideEvent(id: Long): Int

    @Query("SELECT id FROM wide_events WHERE status is not null")
    fun getCompletedWideEventIdsFlow(): Flow<List<Long>>
}
