/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.store.impl.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DuckAiBridgeChatEntity::class, DuckAiBridgeSettingEntity::class, DuckAiBridgeFileMetaEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class DuckAiBridgeDatabase : RoomDatabase() {
    abstract fun chatsDao(): DuckAiBridgeChatsDao
    abstract fun settingsDao(): DuckAiBridgeSettingsDao
    abstract fun fileMetaDao(): DuckAiBridgeFileMetaDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `duck_ai_file_meta` " +
                        "(`uuid` TEXT NOT NULL, `chatId` TEXT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, " +
                        "PRIMARY KEY(`uuid`))",
                )
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_duck_ai_file_meta_chatId` ON `duck_ai_file_meta` (`chatId`)",
                )
            }
        }
    }
}
