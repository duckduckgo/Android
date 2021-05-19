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
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.mobile.android.vpn.dao.*
import com.duckduckgo.mobile.android.vpn.model.*
import com.duckduckgo.mobile.android.vpn.trackers.*
import com.squareup.moshi.JsonAdapter
import com.duckduckgo.mobile.android.vpn.trackers.AppTracker
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerJsonParser.Companion.parseAppTrackerJson
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerMetadata
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerPackage
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter
import timber.log.Timber
import java.util.*
import java.util.concurrent.Executors

@Database(
    exportSchema = true, version = 11,
    entities = [
        VpnState::class,
        VpnTracker::class,
        VpnRunningStats::class,
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
        AppTrackerPackage::class
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
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        ioThread {
                            prepopulateUUID(context)
                            prepopulateAppTrackerBlockingList(context, getInstance(context))
                            prepopulateAppTrackerExclusionList(context, getInstance(context))
                        }
                    }

                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        ioThread {
                            prepopulateUUID(context)
                            prepopulateAppTrackerBlockingList(context, getInstance(context))
                            prepopulateAppTrackerExclusionList(context, getInstance(context))
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

        @VisibleForTesting
        internal fun prepopulateAppTrackerBlockingList(context: Context, vpnDatabase: VpnDatabase) {
            context.resources.openRawResource(R.raw.full_app_trackers_blocklist).bufferedReader()
                .use { it.readText() }
                .also {
                    val trackers = getFullAppTrackerBlockingList(it)
                    with(vpnDatabase.vpnAppTrackerBlockingDao()) {
                        insertTrackerBlocklist(trackers.first)
                        insertAppPackages(trackers.second)
                    }
                }
        }

        @VisibleForTesting
        internal fun prepopulateAppTrackerExclusionList(context: Context, vpnDatabase: VpnDatabase) {
            context.resources.openRawResource(R.raw.app_tracker_app_exclusion_list).bufferedReader()
                .use { it.readText() }
                .also {
                    val excludedAppPackages = parseAppTrackerExclusionList(it)
                    vpnDatabase.vpnAppTrackerBlockingDao().insertExclusionList(excludedAppPackages)
                }
        }

        private fun getFullAppTrackerBlockingList(json: String): Pair<List<AppTracker>, List<AppTrackerPackage>> {
            return parseAppTrackerJson(Moshi.Builder().build(), json)
        }

        private fun parseAppTrackerExclusionList(json: String): List<AppTrackerExcludedPackage> {
            val moshi = Moshi.Builder().build()
            val adapter: JsonAdapter<JsonAppTrackerExclusionList> = moshi.adapter(JsonAppTrackerExclusionList::class.java)
            return adapter.fromJson(json)?.rules.orEmpty().map {
                AppTrackerExcludedPackage(it)
            }
        }

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
}

private val IO_EXECUTOR = Executors.newSingleThreadExecutor()
fun ioThread(f: () -> Unit) {
    IO_EXECUTOR.execute(f)
}
