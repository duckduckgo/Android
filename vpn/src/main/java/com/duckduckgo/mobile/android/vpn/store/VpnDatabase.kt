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
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.mobile.android.vpn.dao.VpnStateDao
import com.duckduckgo.mobile.android.vpn.dao.VpnStatsDao
import com.duckduckgo.mobile.android.vpn.dao.VpnTrackerCompanyDao
import com.duckduckgo.mobile.android.vpn.dao.VpnTrackerDao
import com.duckduckgo.mobile.android.vpn.model.VpnState
import com.duckduckgo.mobile.android.vpn.model.VpnStats
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerCompany
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.TrackerListProvider
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter
import timber.log.Timber
import java.util.*
import java.util.concurrent.Executors

@Database(
    exportSchema = true, version = 1, entities = [
        VpnState::class,
        VpnTracker::class,
        VpnTrackerCompany::class,
        VpnStats::class
    ]
)

@TypeConverters(VpnTypeConverters::class)
abstract class VpnDatabase : RoomDatabase() {

    abstract fun vpnStateDao(): VpnStateDao
    abstract fun vpnTrackerDao(): VpnTrackerDao
    abstract fun vpnTrackerCompanyDao(): VpnTrackerCompanyDao
    abstract fun vpnStatsDao(): VpnStatsDao

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
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        ioThread {
                            prepopulateTrackerCompanies(context)
                            prepopulateUUID(context)
                        }
                    }
                })
                .build()
        }

        private fun prepopulateTrackerCompanies(context: Context) {
            for (trackerGroupCompany in TrackerListProvider.TRACKER_GROUP_COMPANIES) {
                getInstance(context).vpnTrackerCompanyDao().insert(trackerGroupCompany)
            }
        }

        private fun prepopulateUUID(context: Context) {
            val uuid = UUID.randomUUID().toString()
            getInstance(context).vpnStateDao().insert(VpnState(uuid = uuid, isRunning = false))
            Timber.w("VPNDatabase: UUID prepopulated as $uuid")
        }
    }
}

object VpnTypeConverters {

    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

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
}

private val IO_EXECUTOR = Executors.newSingleThreadExecutor()
fun ioThread(f: () -> Unit) {
    IO_EXECUTOR.execute(f)
}
