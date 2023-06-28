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

package com.duckduckgo.sync.store.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duckduckgo.sync.store.model.SyncAttempt
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncAttemptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(attempt: SyncAttempt)

    @Query("SELECT * FROM sync_attempts ORDER BY id DESC LIMIT 1")
    fun lastAttempt(): SyncAttempt?

    @Query("DELETE from sync_attempts")
    fun clear()

    @Query("SELECT * FROM sync_attempts ORDER BY id DESC LIMIT 1")
    fun attempts(): Flow<SyncAttempt?>
}
