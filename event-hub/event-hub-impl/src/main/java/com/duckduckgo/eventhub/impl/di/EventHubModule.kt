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

import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.data.store.api.RoomDatabaseConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.eventhub.impl.pixels.store.ALL_MIGRATIONS
import com.duckduckgo.eventhub.impl.pixels.store.EventHubPixelStateDao
import com.duckduckgo.eventhub.impl.pixels.store.EventHubPixelStateDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EventHubDispatcher

@Module
@ContributesTo(AppScope::class)
object EventHubModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideEventHubPixelStateDatabase(databaseProvider: DatabaseProvider): EventHubPixelStateDatabase {
        return databaseProvider.buildRoomDatabase(
            EventHubPixelStateDatabase::class.java,
            "event_hub_pixel_state.db",
            config = RoomDatabaseConfig(
                fallbackToDestructiveMigration = true,
                migrations = ALL_MIGRATIONS.toList(),
            ),
        )
    }

    @Provides
    fun provideEventHubPixelStateDao(database: EventHubPixelStateDatabase): EventHubPixelStateDao {
        return database.pixelStateDao()
    }

    @SingleInstanceIn(AppScope::class)
    @EventHubDispatcher
    @Provides
    fun provideEventHubDispatcher(): CoroutineDispatcher {
        return Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    }
}
