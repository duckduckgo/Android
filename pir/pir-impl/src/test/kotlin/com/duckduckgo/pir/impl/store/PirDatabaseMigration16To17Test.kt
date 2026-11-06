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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun whenMigratingFrom16To17ThenProfileStoredBeforeExtrasExistedGetsAnEmptyMap() {
        testHelper.createDatabase(TEST_DB_NAME, 16).use { db ->
            db.execSQL(
                """
                INSERT INTO pir_extracted_profiles
                    (id, profileQueryId, brokerName, name, alternativeNames, age, addresses, phoneNumbers,
                     relatives, profileUrl, identifier, reportId, email, fullName, dateAddedInMillis, deprecated)
                VALUES
                    (1, 1, 'broker.example', 'Jane Smith', '[]', '38', '$STORED_ADDRESSES', '[]', '[]',
                     'https://broker.example/jane', 'jane-1', '', '', 'Jane Smith', 1000, 0)
                """.trimIndent(),
            )
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 17, true).use { db ->
            db.query("SELECT extras, addresses, identifier FROM pir_extracted_profiles WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("{}", cursor.getString(0))
                assertEquals(STORED_ADDRESSES, cursor.getString(1))
                assertEquals("jane-1", cursor.getString(2))
            }
        }
    }

    private companion object {
        const val TEST_DB_NAME = "pir_migration_test"

        // An address as it was serialised before extras existed, which the repository must still parse.
        const val STORED_ADDRESSES = """["{\"city\":\"Springfield\",\"state\":\"IL\"}"]"""
    }
}
