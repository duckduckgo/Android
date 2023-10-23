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

package com.duckduckgo.adclick.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.adclick.store.AdClickAttributionRepository
import com.duckduckgo.adclick.store.AdClickDatabase
import com.duckduckgo.adclick.store.AdClickDatabase.Companion.ALL_MIGRATIONS
import com.duckduckgo.adclick.store.AdClickFeatureToggleRepository
import com.duckduckgo.adclick.store.RealAdClickAttributionRepository
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
class AdClickModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideAdClickDatabase(context: Context): AdClickDatabase {
        return Room.databaseBuilder(context, AdClickDatabase::class.java, "adclick.db")
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigration()
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAdClickAttributionRepository(
        database: AdClickDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
    ): AdClickAttributionRepository {
        return RealAdClickAttributionRepository(database, appCoroutineScope, dispatcherProvider)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAdClickFeatureToggleRepository(context: Context): AdClickFeatureToggleRepository {
        return AdClickFeatureToggleRepository.create(context)
    }
}
