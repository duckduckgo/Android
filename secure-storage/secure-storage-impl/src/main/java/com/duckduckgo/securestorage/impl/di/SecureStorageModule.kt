/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.securestorage.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.impl.SecureStorageKeyManager
import com.duckduckgo.securestorage.store.RealSecureStorageKeyStore
import com.duckduckgo.securestorage.store.RealSecureStorageRepository
import com.duckduckgo.securestorage.store.SecureStorageKeyStore
import com.duckduckgo.securestorage.store.SecureStorageRepository
import com.duckduckgo.securestorage.store.db.ALL_MIGRATIONS
import com.duckduckgo.securestorage.store.db.SecureStorageDatabase
import com.duckduckgo.securestorage.store.db.WebsiteLoginCredentialsDao
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import net.sqlcipher.database.SupportFactory

@Module
@ContributesTo(AppScope::class)
object SecureStorageModule {

    @Provides
    fun providesSecureStorageKeyStore(context: Context): SecureStorageKeyStore =
        RealSecureStorageKeyStore(context)

    @Provides
    fun providesSupportFactory(keyManager: SecureStorageKeyManager): SupportFactory {
        return SupportFactory(keyManager.l1Key)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesSecureStorageDatabase(
        context: Context,
        factory: SupportFactory
    ): SecureStorageDatabase {
        return Room.databaseBuilder(
            context,
            SecureStorageDatabase::class.java,
            "secure_storage_database_encrypted.db"
        ).openHelperFactory(factory)
            .addMigrations(*ALL_MIGRATIONS)
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun providesWebsiteLoginCredentialsDao(db: SecureStorageDatabase): WebsiteLoginCredentialsDao {
        return db.websiteLoginCredentialsDao()
    }

    @Provides
    fun providesSecureStorageRepository(websiteLoginCredentialsDao: WebsiteLoginCredentialsDao): SecureStorageRepository =
        RealSecureStorageRepository(websiteLoginCredentialsDao)
}
