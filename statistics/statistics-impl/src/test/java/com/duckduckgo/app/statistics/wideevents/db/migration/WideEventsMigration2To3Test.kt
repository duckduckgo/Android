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
class WideEventsMigration2To3Test {
    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WideEventDatabase::class.java,
        emptyList(),
    )

    @Test
    fun `migration backfills existing events with default sampling probability`() {
        testHelper.createDatabase(TEST_DB_NAME, 2).use { db ->
            db.insertWideEvent(id = 1)
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 3, true, WideEventsMigration2To3).use { db ->
            assertEquals(1.0f, db.readSamplingProbability(id = 1), DELTA)
        }
    }

    @Test
    fun `migration preserves existing event data`() {
        testHelper.createDatabase(TEST_DB_NAME, 2).use { db ->
            db.insertWideEvent(id = 1, name = "event-1", flowEntryPoint = "settings")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 3, true, WideEventsMigration2To3).use { db ->
            assertEquals("event-1", db.readString(id = 1, column = "name"))
            assertEquals("settings", db.readString(id = 1, column = "flow_entry_point"))
            assertEquals(1.0f, db.readSamplingProbability(id = 1), DELTA)
        }
    }

    private companion object {
        const val TEST_DB_NAME = "wide_events_migration_test"
        const val DELTA = 0.0001f
    }
}

private fun SupportSQLiteDatabase.insertWideEvent(
    id: Long,
    name: String = "flow",
    flowEntryPoint: String? = null,
) {
    execSQL(
        """
        INSERT INTO wide_events (
            id, name, created_at, flow_entry_point, metadata, steps, status, cleanup_policy, active_intervals
        ) VALUES (
            ?, ?, 0, ?, '[]', '[]', NULL,
            '{"type":"OnTimeout","duration":600000,"status":"unknown","metadata":{}}',
            '[]'
        )
        """.trimIndent(),
        arrayOf(id, name, flowEntryPoint),
    )
}

private fun SupportSQLiteDatabase.readSamplingProbability(id: Long): Float =
    query("SELECT sampling_probability FROM wide_events WHERE id = ?", arrayOf(id)).use { cursor ->
        cursor.moveToFirst()
        cursor.getFloat(0)
    }

private fun SupportSQLiteDatabase.readString(
    id: Long,
    column: String,
): String? =
    query("SELECT $column FROM wide_events WHERE id = ?", arrayOf(id)).use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    }
