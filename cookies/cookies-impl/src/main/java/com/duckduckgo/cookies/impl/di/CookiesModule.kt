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

package com.duckduckgo.cookies.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.store.ALL_MIGRATIONS
import com.duckduckgo.cookies.store.CookiesDatabase
import com.duckduckgo.cookies.store.CookiesFeatureToggleRepository
import com.duckduckgo.cookies.store.CookiesFeatureToggleStore
import com.duckduckgo.cookies.store.CookiesRepository
import com.duckduckgo.cookies.store.RealCookieRepository
import com.duckduckgo.cookies.store.RealCookiesFeatureToggleRepository
import com.duckduckgo.cookies.store.RealCookiesFeatureToggleStore
import com.duckduckgo.cookies.store.contentscopescripts.ContentScopeScriptsCookieRepository
import com.duckduckgo.cookies.store.contentscopescripts.RealContentScopeScriptsCookieRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object CookiesModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideCookiesDatabase(context: Context): CookiesDatabase {
        return Room.databaseBuilder(context, CookiesDatabase::class.java, "cookies.db")
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideCookiesRepository(
        database: CookiesDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): CookiesRepository {
        return RealCookieRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideCookiesFeatureToggleRepository(cookiesFeatureToggleStore: CookiesFeatureToggleStore): CookiesFeatureToggleRepository {
        return RealCookiesFeatureToggleRepository(cookiesFeatureToggleStore)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideCookiesFeatureToggleStore(context: Context): CookiesFeatureToggleStore {
        return RealCookiesFeatureToggleStore(context)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideContentScopeScriptsCookieRepository(
        database: CookiesDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): ContentScopeScriptsCookieRepository {
        return RealContentScopeScriptsCookieRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }
}
