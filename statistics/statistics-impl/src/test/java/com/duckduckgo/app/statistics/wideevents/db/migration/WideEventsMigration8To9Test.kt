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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WideEventsMigration8To9Test {
    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WideEventDatabase::class.java,
        emptyList(),
    )

    @Test
    fun `migration gives existing events an empty path and no last path step`() {
        testHelper.createDatabase(TEST_DB_NAME, 8).use { db ->
            db.execSQL(
                """
                INSERT INTO wide_events (
                    id, name, created_at, flow_entry_point, metadata, steps, status, cleanup_policy, active_intervals,
                    sampling_probability, meta_type, meta_version, is_first_daily_occurrence, app_version
                ) VALUES (
                    1, 'flow', 0, NULL, '[]', '[{"name":"init","success":true}]', NULL,
                    '{"type":"OnTimeout","duration":600000,"status":"unknown","metadata":{}}',
                    '[]', 1.0, 'android-flow', '1.0.0', 0, '5.300.0'
                )
                """.trimIndent(),
            )
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 9, true, WideEventsMigration8To9).use { db ->
            db.query("SELECT path, last_path_step, steps FROM wide_events WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("[]", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertEquals("""[{"name":"init","success":true}]""", cursor.getString(2))
            }
        }
    }

    private companion object {
        const val TEST_DB_NAME = "wide_events_migration_test"
    }
}
