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

package com.duckduckgo.app.fire.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FireModeDatabaseMigrationTest {

    @get:Rule
    val testHelper = MigrationTestHelper(getInstrumentation(), FireModeDatabase::class.java)

    @Test
    fun whenMigratingFrom1To2ThenFireTabsAndSelectionSurviveAndSessionsTableIsEmpty() {
        testHelper.createDatabase(TEST_DB_NAME, 1).use {
            it.execSQL("INSERT INTO tabs (tabId, skipHome, viewed, position, deletable) VALUES ('fire-tab', 0, 1, 0, 0)")
            it.execSQL("INSERT INTO tab_selection (id, tabId) VALUES (1, 'fire-tab')")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 2, true).use {
            assertEquals(1, it.count("SELECT COUNT(*) FROM tabs WHERE tabId = 'fire-tab'"))
            assertEquals(1, it.count("SELECT COUNT(*) FROM tab_selection WHERE tabId = 'fire-tab'"))
            assertEquals(0, it.count("SELECT COUNT(*) FROM webview_sessions"))
        }
    }

    private fun SupportSQLiteDatabase.count(sql: String): Int = query(sql).use {
        it.moveToFirst()
        it.getInt(0)
    }

    private companion object {
        const val TEST_DB_NAME = "fire-mode-migration-test"
    }
}
