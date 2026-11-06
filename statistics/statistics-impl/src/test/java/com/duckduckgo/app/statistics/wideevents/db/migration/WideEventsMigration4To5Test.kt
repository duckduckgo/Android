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

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.wideevents.db.WideEventDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WideEventsMigration4To5Test {
    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WideEventDatabase::class.java,
        emptyList(),
    )

    @Test
    fun `migration marks existing events as not being the first daily occurrence`() {
        testHelper.createDatabase(TEST_DB_NAME, 4).use { db ->
            db.insertWideEvent(id = 1, name = "some_event_name")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 5, true, WideEventsMigration4To5).use { db ->
            assertEquals(0, db.readInt(id = 1, column = "is_first_daily_occurrence"))
        }
    }

    @Test
    fun `migration preserves existing event data`() {
        testHelper.createDatabase(TEST_DB_NAME, 4).use { db ->
            db.insertWideEvent(id = 1, name = "event-1", flowEntryPoint = "settings", metaType = "android-event-1", metaVersion = "1.2.3")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 5, true, WideEventsMigration4To5).use { db ->
            assertEquals("event-1", db.readString(id = 1, column = "name"))
            assertEquals("settings", db.readString(id = 1, column = "flow_entry_point"))
            assertEquals("android-event-1", db.readString(id = 1, column = "meta_type"))
            assertEquals("1.2.3", db.readString(id = 1, column = "meta_version"))
        }
    }

    @Test
    fun `migration creates a table accepting daily occurrences`() {
        testHelper.createDatabase(TEST_DB_NAME, 4).use { db ->
            db.insertWideEvent(id = 1)
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 5, true, WideEventsMigration4To5).use { db ->
            db.execSQL(
                "INSERT INTO wide_event_daily_occurrences (dedup_key, last_occurrence_date) VALUES (?, ?)",
                arrayOf<Any?>("flow:success", "2026-01-15"),
            )

            assertEquals("2026-01-15", db.readLastOccurrenceDate(dedupKey = "flow:success"))
        }
    }

    private companion object {
        const val TEST_DB_NAME = "wide_events_migration_test"
    }
}

private fun SupportSQLiteDatabase.insertWideEvent(
    id: Long,
    name: String = "flow",
    flowEntryPoint: String? = null,
    metaType: String = "android-flow",
    metaVersion: String = "1.0.0",
) {
    execSQL(
        """
        INSERT INTO wide_events (
            id, name, created_at, flow_entry_point, metadata, steps, status, cleanup_policy, active_intervals,
            sampling_probability, meta_type, meta_version
        ) VALUES (
            ?, ?, 0, ?, '[]', '[]', NULL,
            '{"type":"OnTimeout","duration":600000,"status":"unknown","metadata":{}}',
            '[]', 1.0, ?, ?
        )
        """.trimIndent(),
        arrayOf<Any?>(id, name, flowEntryPoint, metaType, metaVersion),
    )
}

private fun SupportSQLiteDatabase.readInt(
    id: Long,
    column: String,
): Int =
    query("SELECT $column FROM wide_events WHERE id = ?", arrayOf(id)).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

private fun SupportSQLiteDatabase.readString(
    id: Long,
    column: String,
): String? =
    query("SELECT $column FROM wide_events WHERE id = ?", arrayOf(id)).use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    }

private fun SupportSQLiteDatabase.readLastOccurrenceDate(dedupKey: String): String? =
    query("SELECT last_occurrence_date FROM wide_event_daily_occurrences WHERE dedup_key = ?", arrayOf(dedupKey)).use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    }
