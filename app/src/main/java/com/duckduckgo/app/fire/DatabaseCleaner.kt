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

package com.duckduckgo.app.fire

import android.database.sqlite.SQLiteDatabase
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.withContext
import timber.log.Timber

interface DatabaseCleaner {
    suspend fun cleanDatabase(databasePath: String): Boolean
    suspend fun changeJournalModeToDelete(databasePath: String): Boolean
}

class DatabaseCleanerHelper(private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()) : DatabaseCleaner {

    override suspend fun cleanDatabase(databasePath: String): Boolean {
        return executeCommand("VACUUM", databasePath)
    }

    override suspend fun changeJournalModeToDelete(databasePath: String): Boolean {
        return executeCommand("PRAGMA journal_mode=DELETE", databasePath)
    }

    private suspend fun executeCommand(
        command: String,
        databasePath: String
    ): Boolean {
        return withContext(dispatcherProvider.io()) {
            if (databasePath.isNotEmpty()) {
                var commandExecuted = false
                openReadableDatabase(databasePath)?.use { db ->
                    try {
                        db.rawQuery(command, null).use { cursor -> cursor.moveToFirst() }
                        commandExecuted = true
                    } catch (exception: Exception) {
                        Timber.e(exception)
                    }
                }
                return@withContext commandExecuted
            }
            return@withContext false
        }
    }

    private fun openReadableDatabase(databasePath: String): SQLiteDatabase? {
        return try {
            SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE, null)
        } catch (exception: Exception) {
            null
        }
    }
}
