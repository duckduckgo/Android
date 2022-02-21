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

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.mobile.android.vpn.dao.*
import com.duckduckgo.mobile.android.vpn.model.*
import com.duckduckgo.mobile.android.vpn.trackers.*
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerJsonParser.Companion.parseAppTrackerJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter
import timber.log.Timber
import java.util.*
import java.util.concurrent.Executors

@Database(
    exportSchema = true, version = 20,
    entities = [
        VpnState::class,
        VpnTracker::class,
        VpnRunningStats::class,
        VpnServiceStateStats::class,
        VpnDataStats::class,
        VpnPreferences::class,
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
        AppTrackerEntity::class
    ]
)

@TypeConverters(VpnTypeConverters::class)
abstract class VpnDatabase : RoomDatabase() {

    abstract fun vpnStateDao(): VpnStateDao
    abstract fun vpnTrackerDao(): VpnTrackerDao
    abstract fun vpnRunningStatsDao(): VpnRunningStatsDao
    abstract fun vpnDataStatsDao(): VpnDataStatsDao
    abstract fun vpnPreferencesDao(): VpnPreferencesDao
    abstract fun vpnHeartBeatDao(): VpnHeartBeatDao
    abstract fun vpnPhoenixDao(): VpnPhoenixDao
    abstract fun vpnNotificationsDao(): VpnNotificationsDao
    abstract fun vpnAppTrackerBlockingDao(): VpnAppTrackerBlockingDao
    abstract fun vpnServiceStateDao(): VpnServiceStateStatsDao
    abstract fun vpnSystemAppsOverridesDao(): VpnAppTrackerSystemAppsOverridesDao

    companion object {

        @Volatile
        private var INSTANCE: VpnDatabase? = null

        fun getInstance(context: Context): VpnDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): VpnDatabase {
            return Room.databaseBuilder(context, VpnDatabase::class.java, "vpn.db")
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigrationFrom(*IntRange(1, 17).toList().toIntArray())
                .addMigrations(*ALL_MIGRATIONS.toTypedArray())
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        ioThread {
                            prepopulateUUID(context)
                            prepopulateAppTrackerBlockingList(context, getInstance(context))
                            prepopulateAppTrackerExclusionList(context, getInstance(context))
                            prepopulateAppTrackerExceptionRules(context, getInstance(context))
                        }
                    }

                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        ioThread {
                            prepopulateUUID(context)
                            prepopulateAppTrackerBlockingList(context, getInstance(context))
                            prepopulateAppTrackerExclusionList(context, getInstance(context))
                            prepopulateAppTrackerExceptionRules(context, getInstance(context))
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        ioThread {
                            prepopulateTrackerEntities(context, getInstance(context))
                        }
                    }
                })
                .build()
        }

        private fun prepopulateUUID(context: Context) {
            val uuid = UUID.randomUUID().toString()
            getInstance(context).vpnStateDao().insert(VpnState(uuid = uuid))
            Timber.w("VPNDatabase: UUID pre-populated as $uuid")
        }

        internal fun prepopulateTrackerEntities(
            context: Context,
            vpnDatabase: VpnDatabase
        ) {
            context.resources.openRawResource(R.raw.full_app_trackers_blocklist).bufferedReader()
                .use { it.readText() }
                .also {
                    val blocklist = getFullAppTrackerBlockingList(it)
                    with(vpnDatabase.vpnAppTrackerBlockingDao()) {
                        if (!hasTrackerEntities()) {
                            insertTrackerEntities(blocklist.entities)
                        }
                    }
                }
        }

        @VisibleForTesting
        internal fun prepopulateAppTrackerBlockingList(
            context: Context,
            vpnDatabase: VpnDatabase
        ) {
            context.resources.openRawResource(R.raw.full_app_trackers_blocklist).bufferedReader()
                .use { it.readText() }
                .also {
                    val blocklist = getFullAppTrackerBlockingList(it)
                    with(vpnDatabase.vpnAppTrackerBlockingDao()) {
                        insertTrackerBlocklist(blocklist.trackers)
                        insertAppPackages(blocklist.packages)
                        insertTrackerEntities(blocklist.entities)
                    }
                }
        }

        @VisibleForTesting
        internal fun prepopulateAppTrackerExclusionList(
            context: Context,
            vpnDatabase: VpnDatabase
        ) {
            context.resources.openRawResource(R.raw.app_tracker_app_exclusion_list).bufferedReader()
                .use { it.readText() }
                .also {
                    val excludedAppPackages = parseAppTrackerExclusionList(it)
                    vpnDatabase.vpnAppTrackerBlockingDao().insertExclusionList(excludedAppPackages)
                }
        }

        private fun prepopulateAppTrackerExceptionRules(
            context: Context,
            vpnDatabase: VpnDatabase
        ) {
            context.resources.openRawResource(R.raw.app_tracker_exception_rules).bufferedReader()
                .use { it.readText() }
                .also { json ->
                    val rules = parseJsonAppTrackerExceptionRules(json)
                    vpnDatabase.vpnAppTrackerBlockingDao().insertTrackerExceptionRules(rules)
                }
        }

        private fun getFullAppTrackerBlockingList(json: String): AppTrackerBlocklist {
            return parseAppTrackerJson(Moshi.Builder().build(), json)
        }

        private fun parseAppTrackerExclusionList(json: String): List<AppTrackerExcludedPackage> {
            val moshi = Moshi.Builder().build()
            val adapter: JsonAdapter<JsonAppTrackerExclusionList> = moshi.adapter(JsonAppTrackerExclusionList::class.java)
            return adapter.fromJson(json)?.rules.orEmpty().map {
                AppTrackerExcludedPackage(it)
            }
        }

        private fun parseJsonAppTrackerExceptionRules(json: String): List<AppTrackerExceptionRule> {
            val moshi = Moshi.Builder().build()
            val adapter: JsonAdapter<JsonAppTrackerExceptionRules> = moshi.adapter(JsonAppTrackerExceptionRules::class.java)
            return adapter.fromJson(json)?.rules.orEmpty()
        }

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

        val ALL_MIGRATIONS: List<Migration>
            get() = listOf(
                MIGRATION_18_TO_19,
                MIGRATION_19_TO_20
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

private val IO_EXECUTOR = Executors.newSingleThreadExecutor()
fun ioThread(f: () -> Unit) {
    IO_EXECUTOR.execute(f)
}
