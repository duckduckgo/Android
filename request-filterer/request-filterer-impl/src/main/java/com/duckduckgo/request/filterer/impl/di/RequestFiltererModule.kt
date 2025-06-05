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

package com.duckduckgo.request.filterer.impl.di

import android.content.Context
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.data.store.api.RoomDatabaseConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.request.filterer.store.ALL_MIGRATIONS
import com.duckduckgo.request.filterer.store.RealRequestFiltererFeatureToggleRepository
import com.duckduckgo.request.filterer.store.RealRequestFiltererFeatureToggleStore
import com.duckduckgo.request.filterer.store.RealRequestFiltererRepository
import com.duckduckgo.request.filterer.store.RequestFiltererDatabase
import com.duckduckgo.request.filterer.store.RequestFiltererFeatureToggleRepository
import com.duckduckgo.request.filterer.store.RequestFiltererFeatureToggleStore
import com.duckduckgo.request.filterer.store.RequestFiltererRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object RequestFiltererModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideRequestFiltererDatabase(databaseProvider: DatabaseProvider): RequestFiltererDatabase {
        return databaseProvider.buildRoomDatabase(
            RequestFiltererDatabase::class.java,
            "request_filterer.db",
            config = RoomDatabaseConfig(
                fallbackToDestructiveMigration = true,
                migrations = ALL_MIGRATIONS,
            ),
        )
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideRequestFiltererRepository(
        database: RequestFiltererDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): RequestFiltererRepository {
        return RealRequestFiltererRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideRequestFiltererFeatureToggleRepository(
        requestFilterFeatureToggleStore: RequestFiltererFeatureToggleStore,
    ): RequestFiltererFeatureToggleRepository {
        return RealRequestFiltererFeatureToggleRepository(requestFilterFeatureToggleStore)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideRequestFiltererFeatureToggleStore(context: Context): RequestFiltererFeatureToggleStore {
        return RealRequestFiltererFeatureToggleStore(context)
    }
}
