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

package com.duckduckgo.sync.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.duckduckgo.sync.store.dao.SyncAttemptDao
import com.duckduckgo.sync.store.model.SyncAttempt
import com.duckduckgo.sync.store.model.SyncAttemptState

@Database(
    exportSchema = true,
    version = 1,
    entities = [
        SyncAttempt::class,
    ],
)
@TypeConverters(SyncTypeConverters::class)

abstract class SyncDatabase : RoomDatabase() {

    abstract fun syncAttemptsDao(): SyncAttemptDao
}

object SyncTypeConverters {

    @TypeConverter
    @JvmStatic
    fun toSyncState(state: String): SyncAttemptState {
        return try {
            SyncAttemptState.valueOf(state)
        } catch (t: Throwable) {
            SyncAttemptState.FAIL
        }
    }

    @TypeConverter
    @JvmStatic
    fun fromSyncState(syncState: SyncAttemptState): String {
        return syncState.name
    }
}
