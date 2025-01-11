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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.library.loader.LibraryLoader
import com.duckduckgo.securestorage.store.db.ALL_MIGRATIONS
import com.duckduckgo.securestorage.store.db.SecureStorageDatabase
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

interface SecureStorageDatabaseFactory {
    fun getDatabase(): SecureStorageDatabase?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSecureStorageDatabaseFactory @Inject constructor(
    private val context: Context,
    private val keyProvider: SecureStorageKeyProvider,
) : SecureStorageDatabaseFactory {
    private var _database: SecureStorageDatabase? = null

    override fun getDatabase(): SecureStorageDatabase? {
        // If we have already the DB instance then let's use it
        // use double-check locking optimisation
        if (_database != null) {
            return _database
        }

        synchronized(this) {
            if (_database == null) {
                // Ensure the library is loaded before database creation
                try {
                    LibraryLoader.loadLibrary(context, "sqlcipher")
                } catch (t: Throwable) {
                    // error loading the library, return null db
                    return null
                }

                if (keyProvider.canAccessKeyStore()) {
                    _database = Room.databaseBuilder(
                        context,
                        SecureStorageDatabase::class.java,
                        "secure_storage_database_encrypted.db",
                    ).openHelperFactory(SupportOpenHelperFactory(keyProvider.getl1Key()))
                        .addMigrations(*ALL_MIGRATIONS)
                        .build()
                }
            }
        }
        return _database
    }
}
