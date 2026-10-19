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
 * v6 adds the non-null `app_version` column. Rows that predate it get [appVersion], the version running the migration, since the version
 * their flow started on is unknown.
 */
internal class WideEventsMigration5To6(
    private val appVersion: String,
) : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE wide_events RENAME TO wide_events_old")

        db.execSQL(
            """
            CREATE TABLE wide_events (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `flow_entry_point` TEXT,
                `metadata` TEXT NOT NULL,
                `steps` TEXT NOT NULL,
                `status` TEXT,
                `cleanup_policy` TEXT NOT NULL,
                `active_intervals` TEXT NOT NULL,
                `sampling_probability` REAL NOT NULL DEFAULT 1.0,
                `meta_type` TEXT NOT NULL,
                `meta_version` TEXT NOT NULL,
                `is_first_daily_occurrence` INTEGER NOT NULL DEFAULT 0,
                `app_version` TEXT NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT INTO wide_events (
                id, name, created_at, flow_entry_point, metadata, steps, status, cleanup_policy,
                active_intervals, sampling_probability, meta_type, meta_version, is_first_daily_occurrence, app_version
            )
            SELECT
                id, name, created_at, flow_entry_point, metadata, steps, status, cleanup_policy,
                active_intervals, sampling_probability, meta_type, meta_version, is_first_daily_occurrence, ?
            FROM wide_events_old
            """.trimIndent(),
            arrayOf<Any?>(appVersion),
        )

        db.execSQL("DROP TABLE wide_events_old")
    }
}
