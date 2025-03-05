/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.internal.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.pir.internal.store.db.Broker
import com.duckduckgo.pir.internal.store.db.BrokerDao
import com.duckduckgo.pir.internal.store.db.BrokerJsonDao
import com.duckduckgo.pir.internal.store.db.BrokerJsonEtag
import com.duckduckgo.pir.internal.store.db.BrokerOptOut
import com.duckduckgo.pir.internal.store.db.BrokerScan
import com.duckduckgo.pir.internal.store.db.BrokerSchedulingConfig
import com.duckduckgo.pir.internal.store.db.ExtractProfileResult
import com.duckduckgo.pir.internal.store.db.ScanErrorResult
import com.duckduckgo.pir.internal.store.db.ScanNavigateResult
import com.duckduckgo.pir.internal.store.db.ScanResultsDao
import com.duckduckgo.pir.internal.store.db.UserProfile
import com.duckduckgo.pir.internal.store.db.UserProfileDao
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@Database(
    exportSchema = true,
    version = 2,
    entities = [
        BrokerJsonEtag::class,
        Broker::class,
        BrokerOptOut::class,
        BrokerScan::class,
        BrokerSchedulingConfig::class,
        ScanNavigateResult::class,
        ScanErrorResult::class,
        ExtractProfileResult::class,
        UserProfile::class,
    ],
)
@TypeConverters(PirDatabaseConverters::class)
abstract class PirDatabase : RoomDatabase() {
    abstract fun brokerJsonDao(): BrokerJsonDao
    abstract fun brokerDao(): BrokerDao
    abstract fun scanResultsDao(): ScanResultsDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop column is not supported by SQLite, so the data must be moved manually.
                with(database) {
                    execSQL(
                        """ 
                        CREATE TABLE IF NOT EXISTS pir_user_profile (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            -- UserName fields
                            user_firstName TEXT NOT NULL,
                            user_lastName TEXT NOT NULL,
                            user_middleName TEXT,
                            user_suffix TEXT,
                            -- Address fields
                            address_city TEXT NOT NULL,
                            address_state TEXT NOT NULL,
                            address_street TEXT,
                            address_zip TEXT,
                            -- Other fields
                            birthYear INTEGER NOT NULL,
                            phone TEXT,
                            age INTEGER NOT NULL
                        )    
                        """.trimIndent(),
                    )
                }
            }
        }
        val ALL_MIGRATIONS: List<Migration>
            get() = listOf(MIGRATION_1_2)
    }
}

object PirDatabaseConverters {

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
