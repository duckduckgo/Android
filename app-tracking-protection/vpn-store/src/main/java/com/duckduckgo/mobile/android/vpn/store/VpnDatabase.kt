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
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter

@Database(
    exportSchema = true, version = 24,
    entities = [
        VpnState::class,
        VpnTracker::class,
        VpnRunningStats::class,
        VpnServiceStateStats::class,
        VpnDataStats::class,
        HeartBeatEntity::class,
        VpnPhoenixEntity::class,
        VpnNotification::class,
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
    ]
)

@TypeConverters(VpnTypeConverters::class)
abstract class VpnDatabase : RoomDatabase() {

    abstract fun vpnStateDao(): VpnStateDao
    abstract fun vpnTrackerDao(): VpnTrackerDao
    abstract fun vpnRunningStatsDao(): VpnRunningStatsDao
    abstract fun vpnDataStatsDao(): VpnDataStatsDao
    abstract fun vpnHeartBeatDao(): VpnHeartBeatDao
    abstract fun vpnPhoenixDao(): VpnPhoenixDao
    abstract fun vpnNotificationsDao(): VpnNotificationsDao
    abstract fun vpnAppTrackerBlockingDao(): VpnAppTrackerBlockingDao
    abstract fun vpnServiceStateDao(): VpnServiceStateStatsDao
    abstract fun vpnSystemAppsOverridesDao(): VpnAppTrackerSystemAppsOverridesDao
    abstract fun vpnFeatureRemoverDao(): VpnFeatureRemoverDao
    companion object {

        private val MIGRATION_18_TO_19: Migration = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vpn_app_tracker_system_app_override_list`" +
                        " (`packageId` TEXT NOT NULL, PRIMARY KEY (`packageId`))"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vpn_app_tracker_system_app_override_list_metadata`" +
                        " (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eTag` TEXT)"
                )
            }
        }

        private val MIGRATION_19_TO_20: Migration = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vpn_app_tracker_entities`" +
                        " (`trackerCompanyId` INTEGER PRIMARY KEY NOT NULL, `entityName` TEXT NOT NULL, " +
                        "`score` INTEGER NOT NULL, `signals` TEXT NOT NULL)"
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
                        "`isFeatureRemoved` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_22_TO_23: Migration = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vpn_prefs`" +
                        " (`preference` TEXT PRIMARY KEY NOT NULL, " +
                        "`value` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_23_TO_24: Migration = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `vpn_prefs`")
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
