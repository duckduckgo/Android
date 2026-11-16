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

package com.duckduckgo.site.permissions.store

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SitePermissionsMigration6To7Test {
    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SitePermissionsDatabase::class.java,
        emptyList(),
    )

    @Test
    fun whenMigratedThenSitePermissionsAreKeptAndClientToolPermissionsTableExists() {
        testHelper.createDatabase(TEST_DB_NAME, 6).use { db ->
            db.execSQL("INSERT INTO site_permissions (domain, askCameraSetting) VALUES ('example.com', 'ALLOW_ALWAYS')")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 7, true, MIGRATION_6_7).use { db ->
            db.query("SELECT askCameraSetting FROM site_permissions WHERE domain = 'example.com'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("ALLOW_ALWAYS", cursor.getString(0))
            }
            db.execSQL("INSERT INTO client_tool_permissions (origin, toolName, permission) VALUES ('https://duck.ai', 'tool', 'ALWAYS_ALLOW')")
            db.query("SELECT COUNT(*) FROM client_tool_permissions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB_NAME = "site_permissions_migration_6_7_test"
    }
}
