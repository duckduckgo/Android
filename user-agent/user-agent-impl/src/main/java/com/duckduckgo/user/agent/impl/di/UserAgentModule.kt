/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.user.agent.impl.di

import android.content.Context
import android.webkit.WebSettings
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.user.agent.impl.RealUserAgentProvider
import com.duckduckgo.user.agent.store.ALL_MIGRATIONS
import com.duckduckgo.user.agent.store.RealUserAgentFeatureToggleRepository
import com.duckduckgo.user.agent.store.RealUserAgentFeatureToggleStore
import com.duckduckgo.user.agent.store.RealUserAgentRepository
import com.duckduckgo.user.agent.store.UserAgentDatabase
import com.duckduckgo.user.agent.store.UserAgentFeatureToggleRepository
import com.duckduckgo.user.agent.store.UserAgentFeatureToggleStore
import com.duckduckgo.user.agent.store.UserAgentRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import javax.inject.Named

@ContributesTo(AppScope::class)
@Module
class UserAgentModule {
    @SingleInstanceIn(AppScope::class)
    @Provides
    @Named("defaultUserAgent")
    fun provideDefaultUserAgent(context: Context): String {
        return runCatching {
            WebSettings.getDefaultUserAgent(context)
        }.getOrDefault(RealUserAgentProvider.fallbackDefaultUA)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideUserAgentRepository(
        database: UserAgentDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): UserAgentRepository {
        return RealUserAgentRepository(
            database = database,
            coroutineScope = appCoroutineScope,
            dispatcherProvider = dispatcherProvider,
            isMainProcess = isMainProcess,
        )
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideUserAgentFeatureToggleStore(context: Context): UserAgentFeatureToggleStore {
        return RealUserAgentFeatureToggleStore(context)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideUserAgentFeatureToggleRepository(
        userAgentFeatureToggleStore: UserAgentFeatureToggleStore,
    ): UserAgentFeatureToggleRepository {
        return RealUserAgentFeatureToggleRepository(
            userAgentFeatureToggleStore = userAgentFeatureToggleStore,
        )
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideDatabase(context: Context): UserAgentDatabase {
        return Room.databaseBuilder(context, UserAgentDatabase::class.java, "user_agent.db")
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }
}
