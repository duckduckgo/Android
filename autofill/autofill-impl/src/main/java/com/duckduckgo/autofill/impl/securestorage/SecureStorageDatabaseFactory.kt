/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.securestorage

import android.content.Context
import androidx.room.Room
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.store.db.ALL_MIGRATIONS
import com.duckduckgo.autofill.store.db.SecureStorageDatabase
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.library.loader.LibraryLoader
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

interface SecureStorageDatabaseFactory {
    suspend fun getDatabase(): SecureStorageDatabase?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = SecureStorageDatabaseFactory::class,
)
class RealSecureStorageDatabaseFactory @Inject constructor(
    private val context: Context,
    private val keyProvider: SecureStorageKeyProvider,
    private val autofillFeature: AutofillFeature,
) : SecureStorageDatabaseFactory {
    private var _database: SecureStorageDatabase? = null

    private val mutex = Mutex()

    init {
        logcat { "Loading the sqlcipher native library" }
        try {
            LibraryLoader.loadLibrary(context, "sqlcipher")
            logcat { "sqlcipher native library loaded ok" }
        } catch (t: Throwable) {
            // error loading the library
            logcat(ERROR) { "Error loading sqlcipher library: ${t.asLog()}" }
        }
    }

    override suspend fun getDatabase(): SecureStorageDatabase? {
        return if (autofillFeature.createAsyncPreferences().isEnabled()) {
            getAsyncDatabase()
        } else {
            getDatabaseSynchronized()
        }
    }

    @Synchronized
    private fun getDatabaseSynchronized(): SecureStorageDatabase? {
        return runBlocking {
            getInnerDatabase()
        }
    }

    private suspend fun getAsyncDatabase(): SecureStorageDatabase? {
        _database?.let { return it }
        return mutex.withLock {
            getInnerDatabase()
        }
    }

    private suspend fun getInnerDatabase(): SecureStorageDatabase? {
        // If we have already the DB instance then let's use it
        if (_database != null) {
            return _database
        }

        // If we can't access the keystore, it means that L1Key will be null. We don't want to encrypt the db with a null key.
        return if (keyProvider.canAccessKeyStore()) {
            // At this point, we are guaranteed that if l1key is null, it's because it hasn't been generated yet. Else, we always use the one stored.
            _database = Room.databaseBuilder(
                context,
                SecureStorageDatabase::class.java,
                "secure_storage_database_encrypted.db",
            ).openHelperFactory(SupportOpenHelperFactory(keyProvider.getl1Key()))
                .addMigrations(*ALL_MIGRATIONS)
                .build()
            _database
        } else {
            null
        }
    }
}
