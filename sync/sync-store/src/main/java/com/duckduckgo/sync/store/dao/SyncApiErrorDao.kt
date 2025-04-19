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

package com.duckduckgo.sync.store.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duckduckgo.sync.store.model.SyncApiError

@Dao
interface SyncApiErrorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(error: SyncApiError)

    @Query("SELECT * FROM sync_api_errors WHERE date = :date")
    fun errorsByDate(date: String): List<SyncApiError>

    @Query("UPDATE sync_api_errors SET count = count + 1 WHERE feature = :feature AND errorType = :error AND date = :date")
    fun incrementCount(feature: String, error: String, date: String)

    @Query("SELECT * FROM sync_api_errors WHERE feature = :feature AND errorType = :error AND date = :date LIMIT 1")
    fun featureErrorByDate(feature: String, error: String, date: String): SyncApiError?

    @Query("SELECT * FROM sync_api_errors ORDER BY id DESC")
    fun allErrors(): List<SyncApiError>
}
