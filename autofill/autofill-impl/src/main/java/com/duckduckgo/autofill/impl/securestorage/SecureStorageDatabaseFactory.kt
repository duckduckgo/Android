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

import com.duckduckgo.autofill.store.db.ALL_MIGRATIONS
import com.duckduckgo.autofill.store.db.SecureStorageDatabase
import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.data.store.api.RoomDatabaseConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject

interface SecureStorageDatabaseFactory {
    suspend fun getDatabase(): SecureStorageDatabase?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = SecureStorageDatabaseFactory::class,
)
class RealSecureStorageDatabaseFactory @Inject constructor(
    private val keyProvider: SecureStorageKeyProvider,
    private val databaseProvider: DatabaseProvider,
    private val sqlCipherLoader: SqlCipherLibraryLoader,
) : SecureStorageDatabaseFactory {
    private var _database: SecureStorageDatabase? = null
    private val mutex = Mutex()

    override suspend fun getDatabase(): SecureStorageDatabase? {
        _database?.let {
            logcat { "Autofill-DB-Init: Returning existing database instance" }
            return it
        }

        mutex.withLock {
            // If we have already the DB instance then let's use it
            if (_database != null) {
                logcat { "Autofill-DB-Init: Database was initialized while waiting for lock" }
                return _database
            }

            logcat { "Autofill-DB-Init: Lock acquired, waiting for SqlCipher library load" }

            // Wait for sqlcipher library to load with timeout
            sqlCipherLoader.waitForLibraryLoad().getOrElse { throwable ->
                logcat(ERROR) { "Autofill-DB-Init: SqlCipher library load failure - cannot create database: ${throwable.asLog()}" }
                return null
            }
            logcat { "Autofill-DB-Init: SqlCipher library loaded successfully, proceeding with database creation" }

            // If we can't access the keystore, it means that L1Key will be null. We don't want to encrypt the db with a null key.
            return if (keyProvider.canAccessKeyStore()) {
                logcat { "Autofill-DB-Init: Keystore accessible, creating encrypted database" }
                // At this point, we are guaranteed that if l1key is null, it's because it hasn't been generated yet. Else, we always use the one stored.
                _database = databaseProvider.buildRoomDatabase(
                    SecureStorageDatabase::class.java,
                    "secure_storage_database_encrypted.db",
                    config = RoomDatabaseConfig(
                        openHelperFactory = SupportOpenHelperFactory(keyProvider.getl1Key()),
                        migrations = ALL_MIGRATIONS,
                    ),
                )
                logcat { "Autofill-DB-Init: Database created successfully" }
                _database
            } else {
                logcat(ERROR) { "Autofill-DB-Init: Cannot access keystore - database creation aborted" }
                null
            }
        }
    }
}
