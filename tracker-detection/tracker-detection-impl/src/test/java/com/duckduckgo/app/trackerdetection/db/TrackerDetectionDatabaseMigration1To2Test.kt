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

package com.duckduckgo.app.trackerdetection.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerDetectionDatabaseMigration1To2Test {

    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrackerDetectionDatabase::class.java,
        emptyList(),
    )

    @Test
    fun whenMigratingFrom1To2ThenTdsDataIsKeptAndTheLogTablesMatchTheSchema() {
        testHelper.createDatabase(TEST_DB_NAME, 1).use { db ->
            db.execSQL("INSERT INTO tdsMetadata (id, eTag) VALUES (1, 'etag')")
            db.execSQL("INSERT INTO tds_entity (name, displayName, prevalence) VALUES ('Tracker Inc', 'Tracker', 1.5)")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, TrackerDetectionDatabase.MIGRATION_1_2).use { db ->
            assertEquals("etag", db.singleString("SELECT eTag FROM tdsMetadata"))
            assertEquals("Tracker", db.singleString("SELECT displayName FROM tds_entity WHERE name = 'Tracker Inc'"))
            assertEquals("0", db.singleString("SELECT COUNT(*) FROM web_trackers_blocked"))
            assertEquals("0", db.singleString("SELECT COUNT(*) FROM legacy_app_db_imports"))
        }
    }

    // The main and :pir processes both open this database, so each can run the upgrade on the same file.
    @Test
    fun whenMigrationRunsAgainOnAnAlreadyMigratedDatabaseThenItKeepsTheRows() {
        testHelper.createDatabase(TEST_DB_NAME, 1).close()
        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, TrackerDetectionDatabase.MIGRATION_1_2).use { db ->
            db.execSQL("INSERT INTO web_trackers_blocked (trackerUrl, trackerCompany, timestamp) VALUES ('https://t.com', 'T', 'x')")
            TrackerDetectionDatabase.MIGRATION_1_2.migrate(db)

            assertEquals("1", db.singleString("SELECT COUNT(*) FROM web_trackers_blocked"))
        }
    }

    private fun SupportSQLiteDatabase.singleString(sql: String): String =
        query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }

    private companion object {
        const val TEST_DB_NAME = "tracker_detection_migration_test"
    }
}
