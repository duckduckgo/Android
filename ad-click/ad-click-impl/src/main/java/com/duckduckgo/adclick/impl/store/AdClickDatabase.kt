/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.adclick.impl.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    exportSchema = true,
    version = 2,
    entities = [
        AdClickAttributionLinkFormatEntity::class,
        AdClickAttributionAllowlistEntity::class,
        AdClickAttributionExpirationEntity::class,
        AdClickAttributionDetectionEntity::class,
    ],
)
abstract class AdClickDatabase : RoomDatabase() {
    abstract fun adClickDao(): AdClickDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop column is not supported by SQLite, so the data must be moved manually.
                with(database) {
                    execSQL("CREATE TABLE `link_formats_bk` (`url` TEXT NOT NULL, `adDomainParameterName` TEXT NOT NULL, PRIMARY KEY(`url`))")
                    execSQL("INSERT INTO `link_formats_bk` SELECT `url`, `adDomainParameterName` FROM `link_formats`")
                    execSQL("DROP TABLE `link_formats`")
                    execSQL("ALTER TABLE `link_formats_bk` RENAME to `link_formats`")
                }
            }
        }
        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
