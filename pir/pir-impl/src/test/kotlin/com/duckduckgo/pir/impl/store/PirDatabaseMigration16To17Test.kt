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

package com.duckduckgo.pir.impl.store

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PirDatabaseMigration16To17Test {
    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PirDatabase::class.java,
        emptyList(),
    )

    @Test
    fun `migration preserves existing extracted profiles and defaults extras to an empty map`() {
        testHelper.createDatabase(TEST_DB_NAME, 16).use { db ->
            db.insertExtractedProfile(
                id = 1,
                // address JSON as written before extras existed
                addresses = """["{\"city\":\"New York\",\"state\":\"NY\"}"]""",
            )
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 17, true).use { db ->
            assertEquals(1L, db.rowCount())
            assertEquals("""["{\"city\":\"New York\",\"state\":\"NY\"}"]""", db.readColumn(id = 1, column = "addresses"))
            assertEquals("John Doe", db.readColumn(id = 1, column = "name"))
            assertEquals("identifier-123", db.readColumn(id = 1, column = "identifier"))
            assertEquals("1000", db.readColumn(id = 1, column = "dateAddedInMillis"))
            assertEquals("{}", db.readColumn(id = 1, column = "extras"))
        }
    }

    private companion object {
        const val TEST_DB_NAME = "pir_migration_test"
    }
}

private fun SupportSQLiteDatabase.insertExtractedProfile(
    id: Long,
    addresses: String,
) {
    execSQL(
        """
        INSERT INTO pir_extracted_profiles (
            id, profileQueryId, brokerName, name, alternativeNames, age, addresses, phoneNumbers,
            relatives, profileUrl, identifier, reportId, email, fullName, dateAddedInMillis, deprecated
        ) VALUES (
            ?, 1, 'TestBroker', 'John Doe', '[]', '35', ?, '[]',
            '[]', 'https://example.com/profile', 'identifier-123', 'report-1', 'john@example.com',
            'John Doe', 1000, 0
        )
        """.trimIndent(),
        arrayOf<Any>(id, addresses),
    )
}

private fun SupportSQLiteDatabase.readColumn(
    id: Long,
    column: String,
): String? =
    query("SELECT $column FROM pir_extracted_profiles WHERE id = ?", arrayOf(id)).use { cursor ->
        cursor.moveToFirst()
        if (cursor.isNull(0)) null else cursor.getString(0)
    }

private fun SupportSQLiteDatabase.rowCount(): Long =
    query("SELECT COUNT(*) FROM pir_extracted_profiles").use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }
