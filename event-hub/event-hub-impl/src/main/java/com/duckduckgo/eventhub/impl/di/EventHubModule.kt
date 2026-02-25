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

package com.duckduckgo.eventhub.impl.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.eventhub.api.EventHubPixelManager
import com.duckduckgo.eventhub.impl.EventHubDataStore
import com.duckduckgo.eventhub.impl.EventHubPrefs
import com.duckduckgo.eventhub.impl.EventHubRepository
import com.duckduckgo.eventhub.impl.RealEventHubPixelManager
import com.duckduckgo.eventhub.impl.RealEventHubRepository
import com.duckduckgo.eventhub.impl.RealTimeProvider
import com.duckduckgo.eventhub.impl.TimeProvider
import com.duckduckgo.eventhub.impl.store.ALL_MIGRATIONS
import com.duckduckgo.eventhub.impl.store.EventHubPixelStateDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object EventHubModule {

    private val Context.eventHubDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "event_hub",
    )

    @Provides
    @EventHubPrefs
    fun provideEventHubDataStore(context: Context): DataStore<Preferences> = context.eventHubDataStore

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideEventHubPixelStateDatabase(context: Context): EventHubPixelStateDatabase {
        return Room.databaseBuilder(context, EventHubPixelStateDatabase::class.java, "event_hub_pixel_state.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideEventHubRepository(
        dataStore: EventHubDataStore,
        database: EventHubPixelStateDatabase,
    ): EventHubRepository {
        return RealEventHubRepository(dataStore, database.pixelStateDao())
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
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
    ): RealEventHubPixelManager {
        return RealEventHubPixelManager(repository, pixel, timeProvider, appCoroutineScope, dispatcherProvider)
    }

    @Provides
    fun provideEventHubPixelManager(
        real: RealEventHubPixelManager,
    ): EventHubPixelManager = real
}
