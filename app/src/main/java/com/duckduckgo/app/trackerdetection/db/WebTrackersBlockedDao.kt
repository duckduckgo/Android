/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WebTrackersBlockedDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(tracker: WebTrackerBlocked)

    @Query("DELETE FROM web_trackers_blocked")
    suspend fun deleteAll()

    @Query("DELETE FROM web_trackers_blocked WHERE timestamp < :startTime")
    fun deleteOldDataUntil(startTime: String)

    @Query("SELECT * FROM web_trackers_blocked WHERE timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp DESC")
    fun getTrackersBetween(
        startTime: String,
        endTime: String,
    ): Flow<List<WebTrackerBlocked>>

    @Query("SELECT COUNT(*) FROM web_trackers_blocked WHERE timestamp >= :startTime AND timestamp < :endTime")
    suspend fun getTrackersCountBetween(
        startTime: String,
        endTime: String,
    ): Int
}
