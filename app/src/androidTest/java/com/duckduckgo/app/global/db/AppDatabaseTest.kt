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

import android.content.Context
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.global.exception.UncaughtExceptionSource
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppDatabaseTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val testHelper = MigrationTestHelper(getInstrumentation(), AppDatabase::class.qualifiedName, FrameworkSQLiteOpenHelperFactory())

    private val context = mock<Context>()
    private val mockSettingsDataStore = mock<SettingsDataStore>()
    private val mockAddToHomeCapabilityDetector = mock<AddToHomeCapabilityDetector>()

    private val migrationsProvider: MigrationsProvider =
        MigrationsProvider(context, mockSettingsDataStore, mockAddToHomeCapabilityDetector)

    @Before
    fun setup() {
        givenSharedPreferencesEmpty()
    }

    @Test
    fun whenMigratingFromVersion1To2ThenValidationSucceeds() {
        createDatabaseAndMigrate(1, 2, migrationsProvider.MIGRATION_1_TO_2)
    }

    @Test
    fun whenMigratingFromVersion2To3ThenValidationSucceeds() {
        createDatabaseAndMigrate(2, 3, migrationsProvider.MIGRATION_2_TO_3)
    }

    @Test
    fun whenMigratingFromVersion2To3ThenOldLeaderboardDataIsDeleted() {
        testHelper.createDatabase(TEST_DB_NAME, 2).use {
            it.execSQL("INSERT INTO `network_leaderboard` VALUES ('Network2', 'example.com')")

            testHelper.runMigrationsAndValidate(TEST_DB_NAME, 3, true, migrationsProvider.MIGRATION_2_TO_3)

            val count = it.query("SELECT COUNT() FROM network_leaderboard").run {
                moveToNext()
                getInt(0)
            }

            assertEquals(0, count)
        }
    }

    @Test
    fun whenMigratingFromVersion3To4ThenValidationSucceeds() {
        createDatabaseAndMigrate(3, 4, migrationsProvider.MIGRATION_3_TO_4)
    }

    @Test
    fun whenMigratingFromVersion4To5ThenValidationSucceeds() {
        createDatabaseAndMigrate(4, 5, migrationsProvider.MIGRATION_4_TO_5)
    }

    @Test
    fun whenMigratingFromVersion4To5ThenUpdatePositionsOfStoredTabs() {
        testHelper.createDatabase(TEST_DB_NAME, 4).use {
            it.execSQL("INSERT INTO `tabs` values ('tabid1', 'url', 'title') ")
            it.execSQL("INSERT INTO `tabs` values ('tabid2', 'url', 'title') ")

            testHelper.runMigrationsAndValidate(TEST_DB_NAME, 5, true, migrationsProvider.MIGRATION_4_TO_5)

            it.query("SELECT position FROM tabs ORDER BY position").apply {
                moveToNext()
                assertEquals(0, getInt(0))
                moveToNext()
                assertEquals(1, getInt(0))
            }
        }
    }

    @Test
    fun whenMigratingFromVersion4To5ThenTabsAreConsideredViewed() {
        testHelper.createDatabase(TEST_DB_NAME, 4).use {

            it.execSQL("INSERT INTO `tabs` values ('tabid1', 'url', 'title') ")

            testHelper.runMigrationsAndValidate(TEST_DB_NAME, 5, true, migrationsProvider.MIGRATION_4_TO_5)

            val viewed = it.query("SELECT viewed FROM tabs ORDER BY position").run {
                moveToFirst()
                getInt(0) > 0
            }

            assertTrue(viewed)
        }
    }

    @Test
    fun whenMigratingFromVersion5To6ThenValidationSucceeds() {
        createDatabaseAndMigrate(5, 6, migrationsProvider.MIGRATION_5_TO_6)
    }

    @Test
    fun whenMigratingFromVersion6To7ThenValidationSucceeds() {
        createDatabaseAndMigrate(6, 7, migrationsProvider.MIGRATION_6_TO_7)
    }

    @Test
    fun whenMigratingFromVersion7To8ThenValidationSucceeds() {
        createDatabaseAndMigrate(7, 8, migrationsProvider.MIGRATION_7_TO_8)
    }

    @Test
    fun whenMigratingFromVersion8To9ThenValidationSucceeds() {
        createDatabaseAndMigrate(8, 9, migrationsProvider.MIGRATION_8_TO_9)
    }

    @Test
    fun whenMigratingFromVersion9To10ThenValidationSucceeds() {
        createDatabaseAndMigrate(9, 10, migrationsProvider.MIGRATION_9_TO_10)
    }

    @Test
    fun whenMigratingFromVersion10To11ThenValidationSucceeds() {
        createDatabaseAndMigrate(10, 11, migrationsProvider.MIGRATION_10_TO_11)
    }

    @Test
    fun whenMigratingFromVersion11To12ThenValidationSucceeds() {
        createDatabaseAndMigrate(11, 12, migrationsProvider.MIGRATION_11_TO_12)
    }

    @Test
    fun whenMigratingFromVersion11To12ThenTabsDoNotSkipHome() {
        testHelper.createDatabase(TEST_DB_NAME, 11).use {

            it.execSQL("INSERT INTO `tabs` values ('tabid1', 'url', 'title', 1, 0) ")

            testHelper.runMigrationsAndValidate(TEST_DB_NAME, 12, true, migrationsProvider.MIGRATION_11_TO_12)

            val skipHome = it.query("SELECT skipHome FROM tabs ORDER BY position").run {
                moveToFirst()
                getInt(0) > 0
            }

            assertFalse(skipHome)
        }
    }

    @Test
    fun whenMigratingFromVersion12To13ThenValidationSucceeds() {
        createDatabaseAndMigrate(12, 13, migrationsProvider.MIGRATION_12_TO_13)
    }

    @Test
    fun whenMigratingFromVersion13To14ThenValidationSucceeds() {
        createDatabaseAndMigrate(13, 14, migrationsProvider.MIGRATION_13_TO_14)
    }

    @Test
    fun whenMigratingFromVersion14To15ThenValidationSucceeds() {
        createDatabaseAndMigrate(14, 15, migrationsProvider.MIGRATION_14_TO_15)
    }

    @Test
    fun whenMigratingFromVersion15To16ThenValidationSucceeds() {
        createDatabaseAndMigrate(15, 16, migrationsProvider.MIGRATION_15_TO_16)
    }

    @Test
    fun whenMigratingFromVersion16To17ThenValidationSucceeds() {
        createDatabaseAndMigrate(16, 17, migrationsProvider.MIGRATION_16_TO_17)
    }

    @Test
    fun whenMigratingFromVersion17To18ThenValidationSucceeds() {
        createDatabaseAndMigrate(17, 18, migrationsProvider.MIGRATION_17_TO_18)
    }

    @Test
    fun whenMigratingFromVersion17To18IfUserDidNotSeeOnboardingThenMigrateToNew() {
        givenUserNeverSawOnboarding()
        val database = createDatabaseAndMigrate(17, 18, migrationsProvider.MIGRATION_17_TO_18)
        val stage = getUserStage(database)
        assertEquals(AppStage.NEW.name, stage)
    }

    @Test
    fun whenMigratingFromVersion17To18IfUserSeeOnboardingThenMigrateToEstablished() {
        givenUserSawOnboarding()
        val database = createDatabaseAndMigrate(17, 18, migrationsProvider.MIGRATION_17_TO_18)
        val stage = getUserStage(database)
        assertEquals(AppStage.ESTABLISHED.name, stage)
    }

    @Test
    fun whenMigratingFromVersion18To19ThenValidationSucceedsAndRowsDeletedFromTable() {
        testHelper.createDatabase(TEST_DB_NAME, 18).use {

            it.execSQL("INSERT INTO `UncaughtExceptionEntity` values (1, '${UncaughtExceptionSource.GLOBAL.name}', 'message') ")

            testHelper.runMigrationsAndValidate(TEST_DB_NAME, 19, true, migrationsProvider.MIGRATION_18_TO_19)

            val count = it.query("SELECT COUNT() FROM UncaughtExceptionEntity").run {
                moveToFirst()
                getInt(0)
            }

            assertEquals(0, count)
        }
    }

    @Test
    fun whenMigratingFromVersion19To20ThenValidationSucceeds() {
        createDatabaseAndMigrate(19, 20, migrationsProvider.MIGRATION_19_TO_20)
    }

    @Test
    fun whenMigratingFromVersion20To21ThenValidationSucceeds() {
        createDatabaseAndMigrate(20, 21, migrationsProvider.MIGRATION_20_TO_21)
    }

    @Test
    fun whenMigratingFromVersion21To22ThenValidationSucceeds() {
        createDatabaseAndMigrate(21, 22, migrationsProvider.MIGRATION_21_TO_22)
    }

    @Test
    fun whenMigratingFromVersion22To23ThenValidationSucceeds() {
        createDatabaseAndMigrate(22, 23, migrationsProvider.MIGRATION_22_TO_23)
    }

    @Test
    fun whenMigratingFromVersion22To23IfUserStageIsUseOurAppNotificationThenMigrateToEstablished() {
        testHelper.createDatabase(TEST_DB_NAME, 22).use {
            givenUserStageIs(it, AppStage.USE_OUR_APP_NOTIFICATION)

            testHelper.runMigrationsAndValidate(TEST_DB_NAME, 23, true, migrationsProvider.MIGRATION_22_TO_23)
            val stage = getUserStage(it)

            assertEquals(AppStage.ESTABLISHED.name, stage)
        }
    }

    @Test
    fun whenMigratingFromVersion22To23IfUserStageIsNotUseOurAppNotificationThenDoNotMigrateToEstablished() {
        testHelper.createDatabase(TEST_DB_NAME, 22).use {
            givenUserStageIs(it, AppStage.ESTABLISHED)

            testHelper.runMigrationsAndValidate(TEST_DB_NAME, 23, true, migrationsProvider.MIGRATION_22_TO_23)
            val stage = getUserStage(it)

            assertEquals(AppStage.ESTABLISHED.name, stage)
        }
    }

    @Test
    fun whenMigratingFromVersion23To24ThenValidationSucceeds() {
        createDatabaseAndMigrate(23, 24, migrationsProvider.MIGRATION_23_TO_24)
    }

    @Test
    fun whenMigratingFromVersion24To25ThenValidationSucceeds() {
        createDatabaseAndMigrate(24, 25, migrationsProvider.MIGRATION_24_TO_25)
    }

    @Test
    fun whenMigratingFromVersion27To28ThenValidationSucceeds() {
        createDatabaseAndMigrate(27, 28, migrationsProvider.MIGRATION_27_TO_28)
    }

    private fun givenUserStageIs(database: SupportSQLiteDatabase, appStage: AppStage) {
        database.execSQL("INSERT INTO `userStage` values (1, '${appStage.name}') ")
    }

    private fun getUserStage(database: SupportSQLiteDatabase): String {
        var stage: String

        database.query("SELECT appStage from userStage limit 1").apply {
            moveToFirst()
            stage = getString(0)
        }
        return stage
    }

    private fun createDatabase(version: Int) {
        testHelper.createDatabase(TEST_DB_NAME, version).close()
    }

    private fun runMigrations(newVersion: Int, vararg migrations: Migration): SupportSQLiteDatabase {
        return testHelper.runMigrationsAndValidate(TEST_DB_NAME, newVersion, true, *migrations)
    }

    private fun createDatabaseAndMigrate(originalVersion: Int, newVersion: Int, vararg migrations: Migration): SupportSQLiteDatabase {
        createDatabase(originalVersion)
        return runMigrations(newVersion, *migrations)
    }

    private fun givenSharedPreferencesEmpty() {
        val sharedPreferences = mock<SharedPreferences>()
        whenever(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)).thenReturn(sharedPreferences)
    }

    private fun givenUserNeverSawOnboarding() {
        val sharedPreferences = mock<SharedPreferences>()
        whenever(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)).thenReturn(sharedPreferences)
        whenever(sharedPreferences.getInt(eq(PROPERTY_KEY), any())).thenReturn(0)
    }

    private fun givenUserSawOnboarding() {
        val sharedPreferences = mock<SharedPreferences>()
        whenever(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)).thenReturn(sharedPreferences)
        whenever(sharedPreferences.getInt(eq(PROPERTY_KEY), any())).thenReturn(1)
    }

    companion object {
        private const val TEST_DB_NAME = "TEST_DB_NAME"
        private const val FILE_NAME = "com.duckduckgo.app.onboarding.settings"
        private const val PROPERTY_KEY = "com.duckduckgo.app.onboarding.currentVersion"
    }
}
