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

package com.duckduckgo.pir.impl.integration.fakes

import android.content.Context
import androidx.room.Room
import com.duckduckgo.pir.impl.store.PirDatabase
import com.duckduckgo.pir.impl.store.secure.PirSecureStorageDatabaseFactory

/**
 * Fake implementation of PirSecureStorageDatabaseFactory that provides an in-memory Room database.
 * This allows testing with real repositories without needing secure storage or encryption.
 */
class TestPirSecureStorageDatabaseFactory(
    private val context: Context,
) : PirSecureStorageDatabaseFactory {

    private var database: PirDatabase? = null

    override suspend fun getDatabase(): PirDatabase {
        return getDatabaseSync()
    }

    fun getDatabaseSync(): PirDatabase {
        return database ?: createDatabase().also { database = it }
    }

    private fun createDatabase(): PirDatabase {
        return Room
            .inMemoryDatabaseBuilder(context, PirDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    fun closeDatabase() {
        database?.close()
        database = null
    }
}
