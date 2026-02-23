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

package com.duckduckgo.history.impl.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    exportSchema = true,
    version = 2,
    entities = [
        HistoryEntryEntity::class,
        VisitEntity::class,
    ],
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS visits_list_new (" +
                "historyEntryId INTEGER NOT NULL, " +
                "timestamp TEXT NOT NULL, " +
                "tabId TEXT NOT NULL DEFAULT '', " +
                "PRIMARY KEY (timestamp, historyEntryId, tabId), " +
                "FOREIGN KEY (historyEntryId) REFERENCES history_entries(id) ON DELETE CASCADE" +
                ")",
        )
        db.execSQL(
            "INSERT INTO visits_list_new (historyEntryId, timestamp, tabId) " +
                "SELECT historyEntryId, timestamp, '' FROM visits_list",
        )
        db.execSQL("DROP TABLE visits_list")
        db.execSQL("ALTER TABLE visits_list_new RENAME TO visits_list")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
