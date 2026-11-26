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
import androidx.room.Transaction

@Dao
abstract class WebTrackersBlockedDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertAll(trackers: List<WebTrackerBlocked>)

    /**
     * Also marks the legacy import done, so a later import can't bring back the rows the user just cleared.
     */
    @Transaction
    open suspend fun deleteAll() {
        deleteAllRows()
        markLegacyImportDone()
    }

    @Query("DELETE FROM web_trackers_blocked")
    protected abstract fun deleteAllRows()

    @Query("DELETE FROM web_trackers_blocked WHERE timestamp < :startTime")
    abstract fun deleteOldDataUntil(startTime: String)

    @Query("SELECT COUNT(*) FROM web_trackers_blocked WHERE timestamp >= :startTime AND timestamp < :endTime")
    abstract suspend fun getTrackersCountBetween(
        startTime: String,
        endTime: String,
    ): Int

    fun legacyImport(): LegacyAppDbImport = findLegacyImport(LEGACY_TABLE) ?: LegacyAppDbImport(name = LEGACY_TABLE)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveLegacyImport(legacyImport: LegacyAppDbImport)

    fun markLegacyImportDone() {
        saveLegacyImport(LegacyAppDbImport(name = LEGACY_TABLE, done = true))
    }

    @Query("SELECT * FROM legacy_app_db_imports WHERE name = :name")
    protected abstract fun findLegacyImport(name: String): LegacyAppDbImport?

    private companion object {
        const val LEGACY_TABLE = "web_trackers_blocked"
    }
}
