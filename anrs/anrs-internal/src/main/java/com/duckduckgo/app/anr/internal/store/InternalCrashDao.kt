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

package com.duckduckgo.app.anr.internal.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InternalCrashDao {

    @Query("SELECT * FROM crash_events")
    fun getCrashes(): Flow<List<CrashInternalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrash(anr: CrashInternalEntity)

    @Query("DELETE FROM crash_events where timestamp < :removeBeforeTimestamp")
    fun removeOldCrashes(removeBeforeTimestamp: String)
}
