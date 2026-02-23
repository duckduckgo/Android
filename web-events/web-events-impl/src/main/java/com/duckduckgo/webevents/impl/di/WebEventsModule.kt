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

package com.duckduckgo.webevents.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.webevents.impl.EventHubPixelManager
import com.duckduckgo.webevents.impl.RealEventHubPixelManager
import com.duckduckgo.webevents.impl.RealTimeProvider
import com.duckduckgo.webevents.impl.TimeProvider
import com.duckduckgo.webevents.store.ALL_MIGRATIONS
import com.duckduckgo.webevents.store.RealWebEventsRepository
import com.duckduckgo.webevents.store.WebEventsDatabase
import com.duckduckgo.webevents.store.WebEventsRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object WebEventsModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideWebEventsDatabase(context: Context): WebEventsDatabase {
        return Room.databaseBuilder(context, WebEventsDatabase::class.java, "web_events.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideWebEventsRepository(
        database: WebEventsDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): WebEventsRepository {
        return RealWebEventsRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideTimeProvider(): TimeProvider = RealTimeProvider()

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideEventHubPixelManager(
        repository: WebEventsRepository,
        pixel: Pixel,
        timeProvider: TimeProvider,
    ): EventHubPixelManager {
        return RealEventHubPixelManager(repository, pixel, timeProvider)
    }
}
