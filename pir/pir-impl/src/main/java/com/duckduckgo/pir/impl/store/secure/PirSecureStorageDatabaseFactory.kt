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

package com.duckduckgo.pir.impl.store.secure

import android.content.Context
import androidx.room.Room
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.library.loader.LibraryLoader
import com.duckduckgo.pir.impl.store.PirDatabase
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject

interface PirSecureStorageDatabaseFactory {
    suspend fun getDatabase(): PirDatabase?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = PirSecureStorageDatabaseFactory::class,
)
class RealPirSecureStorageDatabaseFactory @Inject constructor(
    private val context: Context,
    private val keyProvider: PirSecureStorageKeyProvider,
) : PirSecureStorageDatabaseFactory {
    private var _database: PirDatabase? = null

    private val mutex = Mutex()

    init {
        logcat { "PIR-DB: Loading the sqlcipher native library" }
        try {
            LibraryLoader.loadLibrary(context, "sqlcipher")
            logcat { "PIR-DB: sqlcipher native library loaded ok" }
        } catch (t: Throwable) {
            // error loading the library
            logcat(ERROR) { "PIR-DB: Error loading sqlcipher library: ${t.asLog()}" }
        }
    }

    override suspend fun getDatabase(): PirDatabase? {
        _database?.let { return it }
        return mutex.withLock {
            getInnerDatabase()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun getInnerDatabase(): PirDatabase? {
        // If we have already the DB instance then let's use it
        if (_database != null) {
            return _database
        }

        // If we can't access the keystore, it means that L1Key will be null. We don't want to encrypt the db with a null key.
        return if (keyProvider.canAccessKeyStore()) {
            // At this point, we are guaranteed that if L1key is null, it's because it hasn't been generated yet. Else, we always use the one stored.
            _database = Room.databaseBuilder(
                context,
                PirDatabase::class.java,
                "pir_encrypted.db",
            )
                .openHelperFactory(
                    SupportOpenHelperFactory(
                        keyProvider.getl1Key(),
                    ),
                )
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration()
                .build()
            _database
        } else {
            logcat(ERROR) { "PIR-DB: Cannot access key store!" }
            null
        }
    }
}
