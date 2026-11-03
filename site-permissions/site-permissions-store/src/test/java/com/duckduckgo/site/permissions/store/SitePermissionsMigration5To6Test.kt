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
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SitePermissionsMigration5To6Test {
    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SitePermissionsDatabase::class.java,
        emptyList(),
    )

    @Test
    fun whenSiteHasExplicitSettingsThenTheyAreKeptAndAskBecomesUnset() {
        testHelper.createDatabase(TEST_DB_NAME, 5).use { db ->
            db.insertSite("example.com", camera = "ALLOW_ALWAYS", mic = "DENY_ALWAYS")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 6, true, MIGRATION_5_6).use { db ->
            assertEquals("ALLOW_ALWAYS", db.readSetting("example.com", "askCameraSetting"))
            assertEquals("DENY_ALWAYS", db.readSetting("example.com", "askMicSetting"))
            assertNull(db.readSetting("example.com", "askDrmSetting"))
            assertNull(db.readSetting("example.com", "askLocationSetting"))
        }
    }

    @Test
    fun whenSiteHasOnlyAskSettingsThenItIsDeleted() {
        testHelper.createDatabase(TEST_DB_NAME, 5).use { db ->
            db.insertSite("example.com")
            db.insertSite("other.com", drm = "ALLOW_ALWAYS")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 6, true, MIGRATION_5_6).use { db ->
            db.query("SELECT domain FROM site_permissions").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("other.com", cursor.getString(0))
            }
        }
    }

    private companion object {
        const val TEST_DB_NAME = "site_permissions_migration_test"
    }
}

private fun SupportSQLiteDatabase.insertSite(
    domain: String,
    camera: String = "ASK_EVERY_TIME",
    mic: String = "ASK_EVERY_TIME",
    drm: String = "ASK_EVERY_TIME",
    location: String = "ASK_EVERY_TIME",
) {
    execSQL(
        "INSERT INTO site_permissions (domain, askCameraSetting, askMicSetting, askDrmSetting, askLocationSetting) VALUES (?, ?, ?, ?, ?)",
        arrayOf<Any?>(domain, camera, mic, drm, location),
    )
}

private fun SupportSQLiteDatabase.readSetting(domain: String, column: String): String? =
    query("SELECT $column FROM site_permissions WHERE domain = ?", arrayOf(domain)).use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    }
