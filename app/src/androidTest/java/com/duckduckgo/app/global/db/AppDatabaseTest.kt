/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.duckduckgo.app.blockingObserve
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppDatabaseTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val testHelper = MigrationTestHelper(getInstrumentation(), AppDatabase::class.qualifiedName, FrameworkSQLiteOpenHelperFactory())

    @Test
    fun whenMigratingFromVersion1To2ThenValidationSucceeds() {
        createDatabaseAndMigrate(1, 2, AppDatabase.MIGRATION_1_TO_2)
    }

    @Test
    fun whenMigratingFromVersion2To3ThenValidationSucceeds() {
        createDatabaseAndMigrate(2, 3, AppDatabase.MIGRATION_2_TO_3)
    }

    @Test
    fun whenMigratingFromVersion2To3ThenOldLeaderboardDataIsDeleted() {
        testHelper.createDatabase(TEST_DB_NAME, 2).use {
            it.execSQL("INSERT INTO `network_leaderboard` VALUES ('Network2', 'example.com')")
        }
        assertTrue(database().networkLeaderboardDao().trackerNetworkLeaderboard().blockingObserve()!!.isEmpty())
    }

    @Test
    fun whenMigratingFromVersion3To4ThenValidationSucceeds() {
        createDatabaseAndMigrate(3, 4, AppDatabase.MIGRATION_3_TO_4)
    }

    @Test
    fun whenMigratingFromVersion4To5ThenValidationSucceeds() {
        createDatabaseAndMigrate(4, 5, AppDatabase.MIGRATION_4_TO_5)
    }

    @Test
    fun whenMigratingFromVersion4To5ThenUpdatePositionsOfStoredTabs() {

        testHelper.createDatabase(TEST_DB_NAME, 4).use {
            it.execSQL("INSERT INTO `tabs` values ('tabid1', 'url', 'title') ")
            it.execSQL("INSERT INTO `tabs` values ('tabid2', 'url', 'title') ")
        }

        assertEquals(0, database().tabsDao().tabs()[0].position)
        assertEquals(1, database().tabsDao().tabs()[1].position)
    }

    @Test
    fun whenMigratingFromVersion4To5ThenTabsAreConsideredViewed() {
        testHelper.createDatabase(TEST_DB_NAME, 4).use {
            it.execSQL("INSERT INTO `tabs` values ('tabid1', 'url', 'title') ")
        }
        assertTrue(database().tabsDao().tabs()[0].viewed)
    }

    @Test
    fun whenMigratingFromVersion5To6ThenValidationSucceeds() {
        createDatabaseAndMigrate(5, 6, AppDatabase.MIGRATION_5_TO_6)
    }

    @Test
    fun whenMigratingFromVersion6To7ThenValidationSucceeds() {
        createDatabaseAndMigrate(6, 7, AppDatabase.MIGRATION_6_TO_7)
    }

    @Test
    fun whenMigratingFromVersion7To8ThenValidationSucceeds() {
        createDatabaseAndMigrate(7, 8, AppDatabase.MIGRATION_7_TO_8)
    }

    @Test
    fun whenMigratingFromVersion8To9ThenValidationSucceeds() {
        createDatabaseAndMigrate(8, 9, AppDatabase.MIGRATION_8_TO_9)
    }

    @Test
    fun whenMigratingFromVersion9To10ThenValidationSucceeds() {
        createDatabaseAndMigrate(9, 10, AppDatabase.MIGRATION_9_TO_10)
    }

    @Test
    fun whenMigratingFromVersion10To11ThenValidationSucceeds() {
        createDatabaseAndMigrate(10, 11, AppDatabase.MIGRATION_10_TO_11)
    }

    @Test
    fun whenMigratingFromVersion11To12ThenValidationSucceeds() {
        createDatabaseAndMigrate(11, 12, AppDatabase.MIGRATION_11_TO_12)
    }

    @Test
    fun whenMigratingFromVersion11To12ThenTabsDoNotSkipHome() {
        testHelper.createDatabase(TEST_DB_NAME, 11).use {
            it.execSQL("INSERT INTO `tabs` values ('tabid1', 'url', 'title', 1, 0) ")
        }
        assertFalse(database().tabsDao().tabs()[0].skipHome)
    }

    @Test
    fun whenMigratingFromVersion12To13ThenValidationSucceeds() {
        createDatabaseAndMigrate(12, 13, AppDatabase.MIGRATION_12_TO_13)
    }

    @Test
    fun whenMigratingFromVersion13To14ThenValidationSucceeds() {
        createDatabaseAndMigrate(13, 14, AppDatabase.MIGRATION_13_TO_14)
    }


    private fun createDatabase(version: Int) {
        testHelper.createDatabase(TEST_DB_NAME, version).close()
    }

    private fun runMigrations(newVersion: Int, vararg migrations: Migration) {
        testHelper.runMigrationsAndValidate(TEST_DB_NAME, newVersion, true, *migrations)
    }

    private fun createDatabaseAndMigrate(originalVersion: Int, newVersion: Int, vararg migrations: Migration) {
        createDatabase(originalVersion)
        runMigrations(newVersion, *migrations)
    }

    private fun database(): AppDatabase {
        val database = Room
            .databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java, TEST_DB_NAME)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS.toTypedArray())
            .allowMainThreadQueries()
            .build()

        testHelper.closeWhenFinished(database)

        return database
    }

    companion object {
        private const val TEST_DB_NAME = "TEST_DB_NAME"
    }
}