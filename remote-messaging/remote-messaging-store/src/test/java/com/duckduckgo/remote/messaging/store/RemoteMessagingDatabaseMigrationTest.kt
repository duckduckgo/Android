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

package com.duckduckgo.remote.messaging.store

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteMessagingDatabaseMigrationTest {

    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RemoteMessagingDatabase::class.java,
        emptyList(),
    )

    @Test
    fun whenMigratingFromV2ToV3ThenFirstShownDateAddedAsNullAndExistingRowPreserved() {
        testHelper.createDatabase(TEST_DB_NAME, 2).apply {
            execSQL("INSERT INTO remote_message (id, message, status, shown) VALUES ('id1', '{}', 'SCHEDULED', 0)")
            close()
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 3, true, *RemoteMessagingDatabase.ALL_MIGRATIONS).apply {
            val cursor = query(
                "SELECT message, status, shown, firstShownDate FROM remote_message WHERE id = 'id1'",
            )
            cursor.moveToFirst()
            assertEquals("{}", cursor.getString(cursor.getColumnIndexOrThrow("message")))
            assertEquals("SCHEDULED", cursor.getString(cursor.getColumnIndexOrThrow("status")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("shown")))
            // the new column is added with a null default for rows that existed before the migration
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("firstShownDate")))
            cursor.close()
            close()
        }
    }

    @Test
    fun whenMigratingToLatestThenValidatesAgainstCurrentSchema() {
        testHelper.createDatabase(TEST_DB_NAME, 2).apply { close() }

        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RemoteMessagingDatabase::class.java,
            TEST_DB_NAME,
        ).addMigrations(*RemoteMessagingDatabase.ALL_MIGRATIONS).build().apply {
            openHelper.writableDatabase
            close()
        }
    }

    companion object {
        private const val TEST_DB_NAME = "remote_messaging_migration_test"
    }
}
