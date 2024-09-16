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

package com.duckduckgo.remote.messaging.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    exportSchema = true,
    version = 2,
    entities = [
        RemoteMessagingConfig::class,
        RemoteMessageEntity::class,
        RemoteMessagingCohort::class,
    ],
)
abstract class RemoteMessagingDatabase : RoomDatabase() {
    abstract fun remoteMessagingConfigDao(): RemoteMessagingConfigDao
    abstract fun remoteMessagesDao(): RemoteMessagesDao
    abstract fun remoteMessagingCohortDao(): RemoteMessagingCohortDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                with(database) {
                    execSQL(
                        "CREATE TABLE IF NOT EXISTS `remote_messaging_cohort` " +
                            "(`messageId` TEXT NOT NULL," +
                            " `percentile` REAL NOT NULL," +
                            " PRIMARY KEY(`messageId`))",
                    )
                }
            }
        }
        val ALL_MIGRATIONS: Array<Migration>
            get() = arrayOf(MIGRATION_1_2)
    }
}
