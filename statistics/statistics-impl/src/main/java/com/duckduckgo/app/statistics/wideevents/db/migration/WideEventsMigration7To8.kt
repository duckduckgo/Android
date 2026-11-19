/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.statistics.wideevents.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONException

/**
 * v8 stores how each active interval rounds its duration to a bucket, and the stored field is non-null. Intervals that predate it
 * were always rounded down, so we backfill them with round_down, and they record the same value they would have before the upgrade.
 *
 * An interval list that cannot be parsed is reset to an empty list so that every row is readable once the migration completes.
 * Aborting instead would leave the database stuck at v7 and fail every wide-event operation until app data is cleared.
 */
internal object WideEventsMigration7To8 : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val updates = db.query("SELECT id, active_intervals FROM wide_events WHERE active_intervals != '$EMPTY_INTERVALS'").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val migratedIntervals = try {
                        val storedIntervals = JSONArray(cursor.getString(1))
                        for (index in 0 until storedIntervals.length()) {
                            storedIntervals.getJSONObject(index).put(ROUNDING_KEY, ROUND_DOWN)
                        }
                        storedIntervals.toString()
                    } catch (e: JSONException) {
                        EMPTY_INTERVALS
                    }
                    add(cursor.getLong(0) to migratedIntervals)
                }
            }
        }

        updates.forEach { (id, intervals) ->
            db.execSQL("UPDATE wide_events SET active_intervals = ? WHERE id = ?", arrayOf<Any?>(intervals, id))
        }
    }

    private const val ROUNDING_KEY = "rounding"
    private const val ROUND_DOWN = "round_down"
    private const val EMPTY_INTERVALS = "[]"
}
