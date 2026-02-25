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

package com.duckduckgo.eventhub.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.eventhub.api.EventHubPixelManager
import com.duckduckgo.eventhub.impl.RealEventHubPixelManager
import com.duckduckgo.eventhub.impl.RealTimeProvider
import com.duckduckgo.eventhub.impl.TimeProvider
import com.duckduckgo.eventhub.store.ALL_MIGRATIONS
import com.duckduckgo.eventhub.store.EventHubDatabase
import com.duckduckgo.eventhub.store.EventHubRepository
import com.duckduckgo.eventhub.store.RealEventHubRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object EventHubModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideEventHubDatabase(context: Context): EventHubDatabase {
        return Room.databaseBuilder(context, EventHubDatabase::class.java, "event_hub.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideEventHubRepository(
        database: EventHubDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): EventHubRepository {
        return RealEventHubRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideTimeProvider(): TimeProvider = RealTimeProvider()

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideRealEventHubPixelManager(
        repository: EventHubRepository,
        pixel: Pixel,
        timeProvider: TimeProvider,
    ): RealEventHubPixelManager {
        return RealEventHubPixelManager(repository, pixel, timeProvider)
    }

    @Provides
    fun provideEventHubPixelManager(
        real: RealEventHubPixelManager,
    ): EventHubPixelManager = real
}
