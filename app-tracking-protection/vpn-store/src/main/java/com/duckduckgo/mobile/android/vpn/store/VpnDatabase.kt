/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.store

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.mobile.android.vpn.dao.*
import com.duckduckgo.mobile.android.vpn.model.*
import com.duckduckgo.mobile.android.vpn.trackers.*
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Database(
    exportSchema = true,
    version = 34,
    entities = [
        VpnTracker::class,
        VpnServiceStateStats::class,
        HeartBeatEntity::class,
        AppTracker::class,
        AppTrackerMetadata::class,
        AppTrackerExcludedPackage::class,
        AppTrackerExclusionListMetadata::class,
        AppTrackerExceptionRule::class,
        AppTrackerExceptionRuleMetadata::class,
        AppTrackerPackage::class,
        AppTrackerManualExcludedApp::class,
        AppTrackerSystemAppOverridePackage::class,
        AppTrackerSystemAppOverrideListMetadata::class,
        AppTrackerEntity::class,
        VpnFeatureRemoverState::class,
    ],
)

@TypeConverters(VpnTypeConverters::class)
abstract class VpnDatabase : RoomDatabase() {

    internal abstract fun vpnTrackerDao(): VpnTrackerDao
    abstract fun vpnHeartBeatDao(): VpnHeartBeatDao
    abstract fun vpnAppTrackerBlockingDao(): VpnAppTrackerBlockingDao
    abstract fun vpnServiceStateDao(): VpnServiceStateStatsDao
    abstract fun vpnSystemAppsOverridesDao(): VpnAppTrackerSystemAppsOverridesDao
    abstract fun vpnFeatureRemoverDao(): VpnFeatureRemoverDao
    companion object {

        private val MIGRATION_18_TO_19: Migration = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vpn_app_tracker_system_app_override_list`" +
                        " (`packageId` TEXT NOT NULL, PRIMARY KEY (`packageId`))",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vpn_app_tracker_system_app_override_list_metadata`" +
                        " (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eTag` TEXT)",
                )
            }
        }

        private val MIGRATION_19_TO_20: Migration = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vpn_app_tracker_entities`" +
                        " (`trackerCompanyId` INTEGER PRIMARY KEY NOT NULL, `entityName` TEXT NOT NULL, " +
                        "`score` INTEGER NOT NULL, `signals` TEXT NOT NULL)",
                )
            }
        }

        private val MIGRATION_20_TO_21: Migration = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `vpn_service_state_stats` ADD COLUMN `stopReason` TEXT NOT NULL DEFAULT 'UNKNOWN' ")
            }
        }

        private val MIGRATION_21_TO_22: Migration = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vpn_feature_remover`" +
                        " (`id` INTEGER PRIMARY KEY NOT NULL, " +
                        "`isFeatureRemoved` INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_22_TO_23: Migration = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vpn_prefs`" +
                        " (`preference` TEXT PRIMARY KEY NOT NULL, " +
                        "`value` INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_23_TO_24: Migration = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `vpn_prefs`")
            }
        }

        private val MIGRATION_24_TO_25: Migration = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vpn_address_lookup`" +
                        " (`address` TEXT PRIMARY KEY NOT NULL, " +
                        "`domain` TEXT NOT NULL)",
                )
            }
        }

        private val MIGRATION_25_TO_26: Migration = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `vpn_data_stats`")
            }
        }

        private val MIGRATION_26_TO_27: Migration = object : Migration(26, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `vpn_service_state_stats` ADD COLUMN `alwaysOnEnabled` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `vpn_service_state_stats` ADD COLUMN `alwaysOnLockedDown` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_27_TO_28: Migration = object : Migration(27, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // https://stackoverflow.com/a/57797179/980345
                // SQLite does not support Alter table operations like Foreign keys

                // delete the old table
                database.execSQL("DROP TABLE IF EXISTS `vpn_tracker`")

                // create the new table
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vpn_tracker`" +
                        "(trackerCompanyId INTEGER NOT NULL, domain TEXT NOT NULL, company TEXT NOT NULL," +
                        "companyDisplayName TEXT NOT NULL, packageId TEXT NOT NULL, appDisplayName TEXT NOT NULL," +
                        "timestamp TEXT NOT NULL, bucket TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(bucket, domain, packageId))",
                )

                database.execSQL("CREATE INDEX IF NOT EXISTS `index_vpn_tracker_bucket` ON `vpn_tracker` (`bucket`)")
            }
        }

        private val MIGRATION_28_TO_29: Migration = object : Migration(28, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `vpn_running_stats`")
            }
        }

        private val MIGRATION_29_TO_30: Migration = object : Migration(29, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `vpn_address_lookup`")
            }
        }

        private val MIGRATION_30_TO_31: Migration = object : Migration(30, 31) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `vpn_state`")
                database.execSQL("DROP TABLE IF EXISTS `vpn_phoenix`")

                database.execSQL(
                    """
                    CREATE TABLE `vpn_service_state_stats_new` (
                      `timestamp` INTEGER NOT NULL,
                      `state` TEXT PRIMARY KEY NOT NULL,
                      `stopReason` TEXT NOT NULL,
                      `alwaysOnEnabled` INTEGER NOT NULL,
                      `alwaysOnLockedDown` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )

                database.execSQL(
                    """
                    INSERT INTO vpn_service_state_stats_new (timestamp, state, stopReason, alwaysOnEnabled, alwaysOnLockedDown)
                    SELECT 1, state, stopReason, alwaysOnEnabled, alwaysOnLockedDown
                    FROM vpn_service_state_stats
                    ORDER BY id DESC limit 1
                    """.trimIndent(),
                )

                database.execSQL("DROP TABLE IF EXISTS `vpn_service_state_stats`")
                database.execSQL("ALTER TABLE `vpn_service_state_stats_new` RENAME TO `vpn_service_state_stats`")
            }
        }

        private val MIGRATION_31_TO_32: Migration = object : Migration(31, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `vpn_app_tracker_exclusion_list` ADD COLUMN `reason` TEXT NOT NULL DEFAULT 'UNKNOWN'")
            }
        }

        private val MIGRATION_32_TO_33: Migration = object : Migration(32, 33) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `vpn_notification`")
            }
        }

        private val MIGRATION_33_TO_34: Migration = object : Migration(33, 34) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM `vpn_app_tracker_blocking_list_metadata`")
            }
        }

        val ALL_MIGRATIONS: List<Migration>
            get() = listOf(
                MIGRATION_18_TO_19,
                MIGRATION_19_TO_20,
                MIGRATION_20_TO_21,
                MIGRATION_21_TO_22,
                MIGRATION_22_TO_23,
                MIGRATION_23_TO_24,
                MIGRATION_24_TO_25,
                MIGRATION_25_TO_26,
                MIGRATION_26_TO_27,
                MIGRATION_27_TO_28,
                MIGRATION_28_TO_29,
                MIGRATION_29_TO_30,
                MIGRATION_30_TO_31,
                MIGRATION_31_TO_32,
                MIGRATION_32_TO_33,
                MIGRATION_33_TO_34,
            )
    }
}

object VpnTypeConverters {

    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter: JsonAdapter<List<String>> = Moshi.Builder().build().adapter(stringListType)

    @TypeConverter
    @JvmStatic
    fun toOffsetDateTime(value: String?): OffsetDateTime? {
        return value?.let {
            return formatter.parse(value, OffsetDateTime::from)
        }
    }

    @TypeConverter
    @JvmStatic
    fun fromOffsetDateTime(date: OffsetDateTime?): String? {
        return date?.format(formatter)
    }

    @TypeConverter
    @JvmStatic
    fun toStringList(value: String): List<String> {
        return stringListAdapter.fromJson(value)!!
    }

    @TypeConverter
    @JvmStatic
    fun fromStringList(value: List<String>): String {
        return stringListAdapter.toJson(value)
    }

    @TypeConverter
    @JvmStatic
    fun toVpnServiceState(state: String): VpnServiceState {
        return try {
            VpnServiceState.valueOf(state)
        } catch (t: Throwable) {
            VpnServiceState.INVALID
        }
    }

    @TypeConverter
    @JvmStatic
    fun fromVpnServiceState(vpnServiceState: VpnServiceState): String {
        return vpnServiceState.name
    }

    @TypeConverter
    @JvmStatic
    fun toVpnStopReason(stopReason: String): VpnStoppingReason {
        return try {
            VpnStoppingReason.valueOf(stopReason)
        } catch (t: Throwable) {
            VpnStoppingReason.UNKNOWN
        }
    }

    @TypeConverter
    @JvmStatic
    fun fromVpnStopReason(vpnStopReason: VpnStoppingReason): String {
        return vpnStopReason.name
    }
}
