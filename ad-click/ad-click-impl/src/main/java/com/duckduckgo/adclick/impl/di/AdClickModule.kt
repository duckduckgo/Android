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

import com.duckduckgo.adclick.impl.AdClickData
import com.duckduckgo.adclick.impl.DuckDuckGoAdClickData
import com.duckduckgo.adclick.impl.remoteconfig.AdClickAttributionFeature
import com.duckduckgo.adclick.impl.remoteconfig.AdClickAttributionRepository
import com.duckduckgo.adclick.impl.remoteconfig.RealAdClickAttributionRepository
import com.duckduckgo.adclick.impl.store.AdClickDatabase
import com.duckduckgo.adclick.impl.store.AdClickDatabase.Companion.ALL_MIGRATIONS
import com.duckduckgo.adclick.impl.store.exemptions.AdClickExemptionsDatabase
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.data.store.api.RoomDatabaseConfig
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
    fun provideAdClickDatabase(databaseProvider: DatabaseProvider): AdClickDatabase {
        return databaseProvider.buildRoomDatabase(
            AdClickDatabase::class.java,
            "adclick.db",
            config = RoomDatabaseConfig(
                fallbackToDestructiveMigration = true,
                migrations = ALL_MIGRATIONS,
            ),
        )
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideAdClickAttributionRepository(
        database: AdClickDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): AdClickAttributionRepository {
        return RealAdClickAttributionRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideAdClickExemptionsDatabase(databaseProvider: DatabaseProvider): AdClickExemptionsDatabase {
        return databaseProvider.buildRoomDatabase(
            AdClickExemptionsDatabase::class.java,
            "adclick_exemptions.db",
            config = RoomDatabaseConfig(fallbackToDestructiveMigration = true),
        )
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideAdClickData(
        database: AdClickExemptionsDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        adClickAttributionFeature: AdClickAttributionFeature,
        @IsMainProcess isMainProcess: Boolean,
    ): AdClickData {
        return DuckDuckGoAdClickData(database, appCoroutineScope, dispatcherProvider, adClickAttributionFeature, isMainProcess)
    }
}
