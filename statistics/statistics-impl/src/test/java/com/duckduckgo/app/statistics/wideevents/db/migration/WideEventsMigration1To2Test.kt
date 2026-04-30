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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.wideevents.db.WideEventDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WideEventsMigration1To2Test {
    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WideEventDatabase::class.java,
        emptyList(),
    )

    @Test
    fun `migration deletes wide events with active intervals`() {
        testHelper.createDatabase(TEST_DB_NAME, 1).use { db ->
            db.insertWideEvent(id = 1, activeIntervals = """[{"name":"k1","started_at":1,"timeout":null}]""")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, WideEventsMigration1To2).use { db ->
            assertEquals(0L, db.rowCount())
        }
    }

    @Test
    fun `migration keeps wide events with no active intervals`() {
        testHelper.createDatabase(TEST_DB_NAME, 1).use { db ->
            db.insertWideEvent(id = 1, activeIntervals = "[]")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, WideEventsMigration1To2).use { db ->
            assertEquals(1L, db.rowCount())
            assertEquals("[]", db.readActiveIntervals(id = 1))
        }
    }

    @Test
    fun `migration handles a mix of rows correctly`() {
        testHelper.createDatabase(TEST_DB_NAME, 1).use { db ->
            db.insertWideEvent(id = 1, activeIntervals = "[]")
            db.insertWideEvent(id = 2, activeIntervals = """[{"name":"k","started_at":1,"timeout":null}]""")
            db.insertWideEvent(id = 3, activeIntervals = "[]")
            // Corrupt JSON is not '[]', so it gets deleted along with normal in-flight rows.
            db.insertWideEvent(id = 4, activeIntervals = "{not json")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, WideEventsMigration1To2).use { db ->
            assertEquals(2L, db.rowCount())
            assertEquals("[]", db.readActiveIntervals(id = 1))
            assertEquals("[]", db.readActiveIntervals(id = 3))
        }
    }

    private companion object {
        const val TEST_DB_NAME = "wide_events_migration_test"
    }
}

private fun androidx.sqlite.db.SupportSQLiteDatabase.insertWideEvent(
    id: Long,
    activeIntervals: String,
) {
    execSQL(
        """
        INSERT INTO wide_events (
            id, name, created_at, flow_entry_point, metadata, steps, status, cleanup_policy, active_intervals
        ) VALUES (
            ?, 'flow', 0, NULL, '[]', '[]', NULL,
            '{"type":"OnTimeout","duration":600000,"status":"unknown","metadata":{}}',
            ?
        )
        """.trimIndent(),
        arrayOf(id, activeIntervals),
    )
}

private fun androidx.sqlite.db.SupportSQLiteDatabase.readActiveIntervals(id: Long): String? =
    query("SELECT active_intervals FROM wide_events WHERE id = ?", arrayOf(id)).use { cursor ->
        if (cursor.moveToFirst()) {
            if (cursor.isNull(0)) null else cursor.getString(0)
        } else {
            null
        }
    }

private fun androidx.sqlite.db.SupportSQLiteDatabase.rowCount(): Long =
    query("SELECT COUNT(*) FROM wide_events").use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }
