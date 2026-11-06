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

/**
 * v5 adds the `is_first_daily_occurrence` flag, along with the table tracking the last day each
 * (event name, status) pair was recorded. Events completed before this migration were never
 * evaluated, so they default to false.
 */
internal val WideEventsMigration4To5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wide_events ADD COLUMN is_first_daily_occurrence INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `wide_event_daily_occurrences` (
                `dedup_key` TEXT NOT NULL,
                `last_occurrence_date` TEXT NOT NULL,
                PRIMARY KEY(`dedup_key`)
            )
            """.trimIndent(),
        )
    }
}
