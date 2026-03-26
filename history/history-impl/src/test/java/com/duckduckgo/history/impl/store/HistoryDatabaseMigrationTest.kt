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

package com.duckduckgo.history.impl.store

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDatabaseMigrationTest {

    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HistoryDatabase::class.java,
        emptyList(),
    )

    @Test
    fun whenMigratingFromV1ToV2ThenExistingVisitsGetEmptyTabId() {
        testHelper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL("INSERT INTO history_entries (id, url, title, query, isSerp) VALUES (1, 'https://example.com', 'Example', NULL, 0)")
            execSQL("INSERT INTO visits_list (historyEntryId, timestamp) VALUES (1, '2024-01-01T00:00:00')")
            close()
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, MIGRATION_1_2).apply {
            val cursor = query("SELECT tabId FROM visits_list WHERE historyEntryId = 1")
            cursor.moveToFirst()
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("tabId")))
            cursor.close()
            close()
        }
    }

    @Test
    fun whenMigratingFromV1ToV2ThenVisitsArePreserved() {
        testHelper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL("INSERT INTO history_entries (id, url, title, query, isSerp) VALUES (1, 'https://example.com', 'Example', NULL, 0)")
            execSQL("INSERT INTO visits_list (historyEntryId, timestamp) VALUES (1, '2024-01-01T00:00:00')")
            execSQL("INSERT INTO visits_list (historyEntryId, timestamp) VALUES (1, '2024-01-02T00:00:00')")
            close()
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, MIGRATION_1_2).apply {
            val cursor = query("SELECT * FROM visits_list WHERE historyEntryId = 1")
            assertEquals(2, cursor.count)
            cursor.close()
            close()
        }
    }

    @Test
    fun whenMigratingFromV1ToV2ThenNewPrimaryKeyIncludesTabId() {
        testHelper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL("INSERT INTO history_entries (id, url, title, query, isSerp) VALUES (1, 'https://example.com', 'Example', NULL, 0)")
            execSQL("INSERT INTO visits_list (historyEntryId, timestamp) VALUES (1, '2024-01-01T00:00:00')")
            close()
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, MIGRATION_1_2).apply {
            // Insert a second visit with same timestamp and historyEntryId but different tabId — should succeed
            val values = ContentValues().apply {
                put("historyEntryId", 1)
                put("timestamp", "2024-01-01T00:00:00")
                put("tabId", "tab1")
            }
            insert("visits_list", SQLiteDatabase.CONFLICT_NONE, values)

            val cursor = query("SELECT * FROM visits_list WHERE historyEntryId = 1")
            assertEquals(2, cursor.count)
            cursor.close()
            close()
        }
    }

    @Test
    fun whenMigratingFromV1ToV2ThenValidatesAgainstCurrentSchema() {
        testHelper.createDatabase(TEST_DB_NAME, 1).apply {
            close()
        }

        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HistoryDatabase::class.java,
            TEST_DB_NAME,
        ).addMigrations(*ALL_MIGRATIONS).build().apply {
            openHelper.writableDatabase
            close()
        }
    }

    companion object {
        private const val TEST_DB_NAME = "history_migration_test"
    }
}
