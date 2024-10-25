/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.store.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.networkprotection.store.remote_config.NetPConfigToggle
import com.duckduckgo.networkprotection.store.remote_config.NetPConfigTogglesDao
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@Database(
    exportSchema = true,
    version = 5,
    entities = [
        NetPManuallyExcludedApp::class,
        NetPConfigToggle::class,
        NetPGeoswitchingLocation::class,
        FlaggedIncompatibleApp::class,
        VpnIncompatibleApp::class,
    ],
)
@TypeConverters(NetpDatabaseConverters::class)
abstract class NetPDatabase : RoomDatabase() {
    abstract fun exclusionListDao(): NetPExclusionListDao
    abstract fun configTogglesDao(): NetPConfigTogglesDao
    abstract fun geoswitchingDao(): NetPGeoswitchingDao
    abstract fun autoExcludeDao(): AutoExcludeDao

    companion object {
        val ALL_MIGRATIONS: List<Migration>
            get() = listOf(
                MIGRATION_3_4,
                MIGRATION_4_5,
            )
        private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vpn_flagged_auto_excluded_apps` (`packageName` TEXT NOT NULL, PRIMARY KEY(`packageName`))",
                )
            }
        }
        private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vpn_auto_excluded_apps` (`packageName` TEXT NOT NULL, PRIMARY KEY(`packageName`))",
                )
            }
        }
    }
}

object NetpDatabaseConverters {

    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter: JsonAdapter<List<String>> = Moshi.Builder().build().adapter(stringListType)

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
