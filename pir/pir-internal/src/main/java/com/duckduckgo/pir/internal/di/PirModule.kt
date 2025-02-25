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

package com.duckduckgo.pir.internal.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.store.PirDatabase
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.RealPirDataStore
import com.duckduckgo.pir.internal.store.RealPirRepository
import com.duckduckgo.pir.internal.store.db.BrokerDao
import com.duckduckgo.pir.internal.store.db.BrokerJsonDao
import com.duckduckgo.pir.internal.store.db.ScanResultsDao
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
class PirModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun bindPirDatabase(context: Context): PirDatabase {
        return Room.databaseBuilder(context, PirDatabase::class.java, "pir.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*PirDatabase.ALL_MIGRATIONS.toTypedArray())
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideBrokerJsonDao(database: PirDatabase): BrokerJsonDao {
        return database.brokerJsonDao()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideBrokerDao(database: PirDatabase): BrokerDao {
        return database.brokerDao()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideScanResultsDao(database: PirDatabase): ScanResultsDao {
        return database.scanResultsDao()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providePirRepository(
        sharedPreferencesProvider: SharedPreferencesProvider,
        dispatcherProvider: DispatcherProvider,
        brokerJsonDao: BrokerJsonDao,
        brokerDao: BrokerDao,
        scanResultsDao: ScanResultsDao,
        currentTimeProvider: CurrentTimeProvider,
        moshi: Moshi,
    ): PirRepository = RealPirRepository(
        dispatcherProvider,
        RealPirDataStore(sharedPreferencesProvider),
        brokerJsonDao,
        brokerDao,
        scanResultsDao,
        currentTimeProvider,
        moshi,
    )
}
