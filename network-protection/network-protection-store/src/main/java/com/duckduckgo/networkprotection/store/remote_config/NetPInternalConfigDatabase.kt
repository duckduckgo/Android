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

package com.duckduckgo.networkprotection.store.remote_config

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types.newParameterizedType

@Database(
    exportSchema = true,
    version = 3,
    entities = [
        NetPConfigToggle::class,
        NetPEgressServer::class,
        SelectedEgressServerName::class,
    ],
)
@TypeConverters(InternalDatabaseConverters::class)
abstract class NetPInternalConfigDatabase : RoomDatabase() {

    abstract fun configTogglesDao(): NetPConfigTogglesDao
    abstract fun serversDao(): NetPServersDao

    companion object {
        fun create(context: Context): NetPInternalConfigDatabase {
            return Room.databaseBuilder(context, NetPInternalConfigDatabase::class.java, "netp_internal.db")
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration()
                .addMigrations(*ALL_MIGRATIONS)
                .build()
        }
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `netp_egress_servers` ADD COLUMN `countryCode` TEXT")
        database.execSQL("ALTER TABLE `netp_egress_servers` ADD COLUMN `city` TEXT")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3)

object InternalDatabaseConverters {

    private val stringListType = newParameterizedType(List::class.java, String::class.java)
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
