/*
 * Copyright (c) 2017 DuckDuckGo
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
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.rating.db.*
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.global.events.db.UserEventEntity
import com.duckduckgo.app.global.events.db.UserEventTypeConverter
import com.duckduckgo.app.global.events.db.UserEventsDao
import com.duckduckgo.app.global.exception.UncaughtExceptionDao
import com.duckduckgo.app.global.exception.UncaughtExceptionEntity
import com.duckduckgo.app.global.exception.UncaughtExceptionSourceConverter
import com.duckduckgo.app.httpsupgrade.model.HttpsBloomFilterSpec
import com.duckduckgo.app.httpsupgrade.model.HttpsFalsePositiveDomain
import com.duckduckgo.app.httpsupgrade.store.HttpsBloomFilterSpecDao
import com.duckduckgo.app.httpsupgrade.store.HttpsFalsePositivesDao
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionsDao
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.onboarding.store.*
import com.duckduckgo.app.privacy.db.*
import com.duckduckgo.app.privacy.model.PrivacyProtectionCountsEntity
import com.duckduckgo.app.privacy.model.UserWhitelistedDomain
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.model.PixelEntity
import com.duckduckgo.app.statistics.model.QueryParamsTypeConverter
import com.duckduckgo.app.statistics.store.PendingPixelDao
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSelectionEntity
import com.duckduckgo.app.trackerdetection.db.*
import com.duckduckgo.app.trackerdetection.model.*
import com.duckduckgo.app.usage.app.AppDaysUsedDao
import com.duckduckgo.app.usage.app.AppDaysUsedEntity
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.app.usage.search.SearchCountEntity

@Database(
    exportSchema = true, version = 27, entities = [
        TdsTracker::class,
        TdsEntity::class,
        TdsDomainEntity::class,
        TemporaryTrackingWhitelistedDomain::class,
        UserWhitelistedDomain::class,
        HttpsBloomFilterSpec::class,
        HttpsFalsePositiveDomain::class,
        NetworkLeaderboardEntry::class,
        SitesVisitedEntity::class,
        TabEntity::class,
        TabSelectionEntity::class,
        BookmarkEntity::class,
        Survey::class,
        DismissedCta::class,
        SearchCountEntity::class,
        AppDaysUsedEntity::class,
        AppEnjoymentEntity::class,
        Notification::class,
        PrivacyProtectionCountsEntity::class,
        UncaughtExceptionEntity::class,
        TdsMetadata::class,
        UserStage::class,
        FireproofWebsiteEntity::class,
        UserEventEntity::class,
        LocationPermissionEntity::class,
        PixelEntity::class
    ]
)

@TypeConverters(
    Survey.StatusTypeConverter::class,
    DismissedCta.IdTypeConverter::class,
    AppEnjoymentTypeConverter::class,
    PromptCountConverter::class,
    ActionTypeConverter::class,
    RuleTypeConverter::class,
    CategoriesTypeConverter::class,
    UncaughtExceptionSourceConverter::class,
    StageTypeConverter::class,
    UserEventTypeConverter::class,
    LocationPermissionTypeConverter::class,
    QueryParamsTypeConverter::class
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tdsTrackerDao(): TdsTrackerDao
    abstract fun tdsEntityDao(): TdsEntityDao
    abstract fun tdsDomainEntityDao(): TdsDomainEntityDao
    abstract fun temporaryTrackingWhitelistDao(): TemporaryTrackingWhitelistDao
    abstract fun userWhitelistDao(): UserWhitelistDao
    abstract fun httpsFalsePositivesDao(): HttpsFalsePositivesDao
    abstract fun httpsBloomFilterSpecDao(): HttpsBloomFilterSpecDao
    abstract fun networkLeaderboardDao(): NetworkLeaderboardDao
    abstract fun tabsDao(): TabsDao
    abstract fun bookmarksDao(): BookmarksDao
    abstract fun surveyDao(): SurveyDao
    abstract fun dismissedCtaDao(): DismissedCtaDao
    abstract fun searchCountDao(): SearchCountDao
    abstract fun appsDaysUsedDao(): AppDaysUsedDao
    abstract fun appEnjoymentDao(): AppEnjoymentDao
    abstract fun notificationDao(): NotificationDao
    abstract fun privacyProtectionCountsDao(): PrivacyProtectionCountDao
    abstract fun uncaughtExceptionDao(): UncaughtExceptionDao
    abstract fun tdsDao(): TdsMetadataDao
    abstract fun userStageDao(): UserStageDao
    abstract fun fireproofWebsiteDao(): FireproofWebsiteDao
    abstract fun locationPermissionsDao(): LocationPermissionsDao
    abstract fun userEventsDao(): UserEventsDao
    abstract fun pixelDao(): PendingPixelDao
}

@Suppress("PropertyName")
class MigrationsProvider(
    val context: Context,
    val settingsDataStore: SettingsDataStore,
    val addToHomeCapabilityDetector: AddToHomeCapabilityDetector
) {

    val MIGRATION_1_TO_2: Migration = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE `tabs` (`tabId` TEXT NOT NULL, `url` TEXT, `title` TEXT, PRIMARY KEY(`tabId`))")
            database.execSQL("CREATE INDEX `index_tabs_tabId` on `tabs` (tabId)")
            database.execSQL("CREATE TABLE `tab_selection` (`id` INTEGER NOT NULL, `tabId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`tabId`) REFERENCES `tabs`(`tabId`) ON UPDATE NO ACTION ON DELETE SET NULL)")
            database.execSQL("CREATE INDEX `index_tab_selection_tabId` on `tab_selection` (tabId)")
        }
    }

    val MIGRATION_2_TO_3: Migration = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE `site_visited` (`domain` TEXT NOT NULL, PRIMARY KEY(`domain`))")
            database.execSQL("DELETE FROM `network_leaderboard`")
        }
    }

    val MIGRATION_3_TO_4: Migration = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE `https_upgrade_domain`")
            database.execSQL("CREATE TABLE `https_bloom_filter_spec` (`id` INTEGER NOT NULL, `errorRate` REAL NOT NULL, `totalEntries` INTEGER NOT NULL, `sha256` TEXT NOT NULL, PRIMARY KEY(`id`))")
            database.execSQL("CREATE TABLE `https_whitelisted_domain` (`domain` TEXT NOT NULL, PRIMARY KEY(`domain`))")
        }
    }

    val MIGRATION_4_TO_5: Migration = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `tabs` ADD COLUMN `viewed` INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE `tabs` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")

            database.query("SELECT `tabId` from `tabs`").use {
                if (it.moveToFirst()) {
                    var index = 0
                    do {
                        val tabId = it.getString(it.getColumnIndex("tabId"))
                        database.execSQL("UPDATE `tabs` SET position=$index where `tabId` = \"$tabId\"")
                        index += 1
                    } while (it.moveToNext())
                }
            }
        }
    }

    val MIGRATION_5_TO_6: Migration = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `entity_list` (`entityName` TEXT NOT NULL, `domainName` TEXT NOT NULL, PRIMARY KEY(`domainName`))")
        }
    }

    val MIGRATION_6_TO_7: Migration = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `survey` (`surveyId` TEXT NOT NULL, `url` TEXT, `daysInstalled` INTEGER, `status` TEXT NOT NULL, PRIMARY KEY(`surveyId`))")
        }
    }

    val MIGRATION_7_TO_8: Migration = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `dismissed_cta` (`ctaId` TEXT NOT NULL, PRIMARY KEY(`ctaId`))")
        }
    }

    val MIGRATION_8_TO_9: Migration = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `notification` (`notificationId` TEXT NOT NULL, PRIMARY KEY(`notificationId`))")
        }
    }

    val MIGRATION_9_TO_10: Migration = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `search_count` (`key` TEXT NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            database.execSQL("CREATE TABLE IF NOT EXISTS `app_days_used` (`date` TEXT NOT NULL, PRIMARY KEY(`date`))")
            database.execSQL("CREATE TABLE IF NOT EXISTS `app_enjoyment` (`eventType` INTEGER NOT NULL, `promptCount` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `primaryKey` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        }
    }

    val MIGRATION_10_TO_11: Migration = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `privacy_protection_count` (`key` TEXT NOT NULL, `blocked_tracker_count` INTEGER NOT NULL, `upgrade_count` INTEGER NOT NULL, PRIMARY KEY(`key`))")
        }
    }

    val MIGRATION_11_TO_12: Migration = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `tabs` ADD COLUMN `skipHome` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_12_TO_13: Migration = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE `site_visited`")
            database.execSQL("DROP TABLE `network_leaderboard`")
            database.execSQL("CREATE TABLE IF NOT EXISTS `sites_visited` (`key` TEXT NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            database.execSQL("CREATE TABLE IF NOT EXISTS `network_leaderboard` (`networkName` TEXT NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`networkName`))")
        }
    }

    val MIGRATION_13_TO_14: Migration = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `tabs` ADD COLUMN `tabPreviewFile` TEXT")
        }
    }

    val MIGRATION_14_TO_15: Migration = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `UncaughtExceptionEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `exceptionSource` TEXT NOT NULL, `message` TEXT NOT NULL)")
        }
    }

    val MIGRATION_15_TO_16: Migration = object : Migration(15, 16) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE `app_configuration`")
            database.execSQL("DROP TABLE `disconnect_tracker`")
            database.execSQL("DROP TABLE `entity_list`")
            database.execSQL("DELETE FROM `network_leaderboard`")
            database.execSQL("CREATE TABLE IF NOT EXISTS `tds_tracker` (`domain` TEXT NOT NULL, `defaultAction` TEXT NOT NULL, `ownerName` TEXT NOT NULL, `rules` TEXT NOT NULL, `categories` TEXT NOT NULL, PRIMARY KEY(`domain`))")
            database.execSQL("CREATE TABLE IF NOT EXISTS `tds_entity` (`name` TEXT NOT NULL, `displayName` TEXT NOT NULL, `prevalence` REAL NOT NULL, PRIMARY KEY(`name`))")
            database.execSQL("CREATE TABLE IF NOT EXISTS `tds_domain_entity` (`domain` TEXT NOT NULL, `entityName` TEXT NOT NULL, PRIMARY KEY(`domain`))")
            database.execSQL("CREATE TABLE IF NOT EXISTS `temporary_tracking_whitelist` (`domain` TEXT NOT NULL, PRIMARY KEY(`domain`))")
        }
    }

    val MIGRATION_16_TO_17: Migration = object : Migration(16, 17) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `tdsMetadata` (`id` INTEGER NOT NULL, `eTag` TEXT NOT NULL, PRIMARY KEY(`id`))")
        }
    }

    @Suppress("DEPRECATION")
    val MIGRATION_17_TO_18: Migration = object : Migration(17, 18) {

        val onboardingStore: OldOnboardingStore = OldOnboardingStore()

        override fun migrate(database: SupportSQLiteDatabase) {
            val appStage = if (onboardingStore.shouldShow()) {
                AppStage.NEW
            } else {
                AppStage.ESTABLISHED
            }
            val userStage = UserStage(appStage = appStage)
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `$USER_STAGE_TABLE_NAME` " +
                        "(`key` INTEGER NOT NULL, `appStage` TEXT NOT NULL, PRIMARY KEY(`key`))"
            )
            database.execSQL(
                "INSERT INTO $USER_STAGE_TABLE_NAME VALUES (${userStage.key}, \"${userStage.appStage}\") "
            )
        }
    }

    val MIGRATION_18_TO_19: Migration = object : Migration(18, 19) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE `UncaughtExceptionEntity`")
            database.execSQL("CREATE TABLE IF NOT EXISTS `UncaughtExceptionEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `exceptionSource` TEXT NOT NULL, `message` TEXT NOT NULL, `version` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
        }
    }

    val MIGRATION_19_TO_20: Migration = object : Migration(19, 20) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `user_whitelist` (`domain` TEXT NOT NULL, PRIMARY KEY(`domain`))")
        }
    }

    val MIGRATION_20_TO_21: Migration = object : Migration(20, 21) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `fireproofWebsites` (`domain` TEXT NOT NULL, PRIMARY KEY(`domain`))")
        }
    }

    val MIGRATION_21_TO_22: Migration = object : Migration(21, 22) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `user_events` (`id` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        }
    }

    val MIGRATION_22_TO_23: Migration = object : Migration(22, 23) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("UPDATE $USER_STAGE_TABLE_NAME SET appStage = \"${AppStage.ESTABLISHED}\" WHERE appStage = \"${AppStage.USE_OUR_APP_NOTIFICATION}\"")
        }
    }

    val MIGRATION_23_TO_24: Migration = object : Migration(23, 24) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // https://stackoverflow.com/a/57797179/980345
            // SQLite does not support Alter table operations like Foreign keys
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS tabs_new " +
                        "(tabId TEXT NOT NULL, url TEXT, title TEXT, skipHome INTEGER NOT NULL, viewed INTEGER NOT NULL, position INTEGER NOT NULL, tabPreviewFile TEXT, sourceTabId TEXT," +
                        " PRIMARY KEY(tabId)," +
                        " FOREIGN KEY(sourceTabId) REFERENCES tabs(tabId) ON UPDATE SET NULL ON DELETE SET NULL )"
            )
            database.execSQL(
                "INSERT INTO tabs_new (tabId, url, title, skipHome, viewed, position, tabPreviewFile) " +
                        "SELECT tabId, url, title, skipHome, viewed, position, tabPreviewFile " +
                        "FROM tabs"
            )
            database.execSQL("DROP TABLE tabs")
            database.execSQL("ALTER TABLE tabs_new RENAME TO tabs")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_tabs_tabId ON tabs (tabId)")
        }
    }

    val MIGRATION_24_TO_25: Migration = object : Migration(24, 25) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `locationPermissions` (`domain` TEXT NOT NULL, `permission` INTEGER NOT NULL, PRIMARY KEY(`domain`))")
        }
    }

    val MIGRATION_25_TO_26: Migration = object : Migration(25, 26) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE `https_bloom_filter_spec`")
            database.execSQL("DROP TABLE `https_whitelisted_domain`")
            database.execSQL("CREATE TABLE `https_bloom_filter_spec` (`id` INTEGER NOT NULL, `bitCount` INTEGER NOT NULL, `errorRate` REAL NOT NULL, `totalEntries` INTEGER NOT NULL, `sha256` TEXT NOT NULL, PRIMARY KEY(`id`))")
            database.execSQL("CREATE TABLE `https_false_positive_domain` (`domain` TEXT NOT NULL, PRIMARY KEY(`domain`))")
        }
    }

    val MIGRATION_26_TO_27: Migration = object : Migration(26, 27) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `pixel_store` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pixelName` TEXT NOT NULL, `atb` TEXT NOT NULL, `additionalQueryParams` TEXT NOT NULL, `encodedQueryParams` TEXT NOT NULL)")
        }
    }

    val ALL_MIGRATIONS: List<Migration>
        get() = listOf(
            MIGRATION_1_TO_2,
            MIGRATION_2_TO_3,
            MIGRATION_3_TO_4,
            MIGRATION_4_TO_5,
            MIGRATION_5_TO_6,
            MIGRATION_6_TO_7,
            MIGRATION_7_TO_8,
            MIGRATION_8_TO_9,
            MIGRATION_9_TO_10,
            MIGRATION_10_TO_11,
            MIGRATION_11_TO_12,
            MIGRATION_12_TO_13,
            MIGRATION_13_TO_14,
            MIGRATION_14_TO_15,
            MIGRATION_15_TO_16,
            MIGRATION_16_TO_17,
            MIGRATION_17_TO_18,
            MIGRATION_18_TO_19,
            MIGRATION_19_TO_20,
            MIGRATION_20_TO_21,
            MIGRATION_21_TO_22,
            MIGRATION_22_TO_23,
            MIGRATION_23_TO_24,
            MIGRATION_24_TO_25,
            MIGRATION_25_TO_26,
            MIGRATION_26_TO_27
        )

    @Deprecated(
        message = "This class should be only used by database migrations.",
        replaceWith = ReplaceWith(expression = "UserStageStore", imports = ["com.duckduckgo.app.onboarding.store"])
    )
    private inner class OldOnboardingStore {
        private val fileName = "com.duckduckgo.app.onboarding.settings"
        private val keyVersion = "com.duckduckgo.app.onboarding.currentVersion"
        private val currentVersion = 1

        fun shouldShow(): Boolean {
            val preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            return preferences.getInt(keyVersion, 0) < currentVersion
        }
    }
}
