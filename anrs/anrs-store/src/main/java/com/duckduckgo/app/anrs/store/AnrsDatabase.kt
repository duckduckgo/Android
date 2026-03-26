/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.anrs.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types.newParameterizedType

@Database(
    exportSchema = true,
    version = 4,
    entities = [AnrEntity::class],
)
@TypeConverters(AnrTypeConverter::class)
abstract class AnrsDatabase : RoomDatabase() {
    abstract fun arnDao(): AnrDao

    companion object {

        private val MIGRATION_1_TO_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `anr_entity` ADD COLUMN `webView` TEXT NOT NULL DEFAULT \"unknown\"")
            }
        }

        private val MIGRATION_2_TO_3: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `anr_entity` ADD COLUMN `customTab` TEXT NOT NULL DEFAULT \"false\"")
            }
        }

        private val MIGRATION_3_TO_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `anr_entity` ADD COLUMN `appVersion` TEXT NOT NULL DEFAULT \"unknown\"")
            }
        }

        val ALL_MIGRATIONS: List<Migration>
            get() = listOf(
                MIGRATION_1_TO_2,
                MIGRATION_2_TO_3,
                MIGRATION_3_TO_4,
            )
    }
}

object AnrTypeConverter {

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
