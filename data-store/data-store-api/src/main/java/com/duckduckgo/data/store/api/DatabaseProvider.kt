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

package com.duckduckgo.data.store.api

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteOpenHelper

/**
 * Configuration options for building a Room database
 * @property openHelperFactory Custom SQLite open helper factory (e.g., for encryption). Null by default.
 * @property enableMultiInstanceInvalidation Whether to enable multi-instance invalidation. False by default.
 * @property journalMode The journal mode to use for the database. Null by default.
 * @property callbacks List of database callbacks to be added. Empty list by default.
 * @property fallbackToDestructiveMigration If true, the database will be recreated if a migration is not found. False by default.
 * @property fallbackToDestructiveMigrationFromVersion List of versions to fallback to destructive migration from. Empty list by default.
 * @property migrations List of migrations to apply to the database. Empty list by default.
 */
data class RoomDatabaseConfig(
    val openHelperFactory: SupportSQLiteOpenHelper.Factory? = null,
    val enableMultiInstanceInvalidation: Boolean = false,
    val journalMode: RoomDatabase.JournalMode? = null,
    val callbacks: List<RoomDatabase.Callback> = emptyList(),
    val fallbackToDestructiveMigration: Boolean = false,
    val fallbackToDestructiveMigrationFromVersion: List<Int> = emptyList(),
    val migrations: List<Migration> = emptyList(),
    val executor: DatabaseExecutor = DatabaseExecutor.Default,
)

sealed class DatabaseExecutor {

    /**
     * Custom executor configuration for Room database operations.
     * @property transactionPoolSize The size of the thread pool for transaction operations.
     * @property queryPoolSize The size of the thread pool for query operations.
     * @property transactionQueueSize Optional size of the queue for transaction operations. If not set, 2 * [transactionPoolSize] will be used.
     * @property queryQueueSize Optional size of the queue for query operations. If not set, 2 * [queryPoolSize] will be used.
     */
    class Custom(
        val transactionPoolSize: Int,
        val queryPoolSize: Int,
        val transactionQueueSize: Int = 2 * transactionPoolSize,
        val queryQueueSize: Int = 2 * queryPoolSize,
    ) : DatabaseExecutor()

    data object Default : DatabaseExecutor()
}

interface DatabaseProvider {
    /**
     * @param klass - The abstract class which is annotated with Database and extends RoomDatabase.
     * @param name - The name of the database file.
     * @param T - The type of the database class.
     * @param config - Optional configuration for the database build process. Empty by default.
     * @return T: RoomDatabase - A database
     */
    fun<T : RoomDatabase> buildRoomDatabase(
        klass: Class<T>,
        name: String,
        config: RoomDatabaseConfig = RoomDatabaseConfig(),
    ): T
}
