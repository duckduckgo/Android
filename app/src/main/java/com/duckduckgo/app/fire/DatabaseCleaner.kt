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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

interface DatabaseCleaner {
    suspend fun cleanDatabase(): Boolean
}

class AuthDatabaseCleaner(private val databaseLocator: DatabaseLocator) : DatabaseCleaner {

    override suspend fun cleanDatabase(): Boolean {
        return withContext(Dispatchers.IO) {
            val databasePath: String = databaseLocator.getDatabasePath()
            if (databasePath.isNotEmpty()) {
                return@withContext cleanUpDatabase(databasePath)
            }
            return@withContext false
        }
    }

    private fun cleanUpDatabase(databasePath: String): Boolean {
        var cleanUpExecuted = false
        openReadableDatabase(databasePath)?.apply {
            try {
                execSQL("VACUUM")
                cleanUpExecuted = true
            } catch (exception: Exception) {
                Timber.e(exception)
            } finally {
                close()
            }
        }
        return cleanUpExecuted
    }

    private fun openReadableDatabase(databasePath: String): SQLiteDatabase? {
        return try {
            SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE, null)
        } catch (exception: Exception) {
            null
        }
    }
}
