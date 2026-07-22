/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.duckduckgo.adblocking.impl.store.AD_BLOCKING_EXTENSION_MIGRATIONS
import com.duckduckgo.adblocking.impl.store.AdBlockingExtensionDao
import com.duckduckgo.adblocking.impl.store.AdBlockingExtensionDatabase
import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.data.store.api.RoomDatabaseConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import javax.inject.Qualifier

@Module
@ContributesTo(AppScope::class)
object AdBlockingExtensionModule {

    private val Context.adBlockingDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "ad_blocking",
    )

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAdBlockingExtensionDatabase(databaseProvider: DatabaseProvider): AdBlockingExtensionDatabase {
        return databaseProvider.buildRoomDatabase(
            AdBlockingExtensionDatabase::class.java,
            "ad_blocking_extension.db",
            config = RoomDatabaseConfig(
                fallbackToDestructiveMigration = true,
                migrations = AD_BLOCKING_EXTENSION_MIGRATIONS.toList(),
            ),
        )
    }

    @Provides
    fun provideAdBlockingExtensionDao(database: AdBlockingExtensionDatabase): AdBlockingExtensionDao =
        database.adBlockingExtensionDao()

    @SingleInstanceIn(AppScope::class)
    @Provides
    @AdBlockingPreferences
    fun provideAdBlockingDataStore(context: Context): DataStore<Preferences> =
        context.adBlockingDataStore
}

@Qualifier
internal annotation class AdBlockingPreferences
