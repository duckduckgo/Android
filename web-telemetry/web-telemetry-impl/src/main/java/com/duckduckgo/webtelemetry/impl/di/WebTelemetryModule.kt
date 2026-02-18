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

package com.duckduckgo.webtelemetry.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.webtelemetry.impl.EventHubPixelManager
import com.duckduckgo.webtelemetry.impl.RealEventHubPixelManager
import com.duckduckgo.webtelemetry.impl.RealStaggerProvider
import com.duckduckgo.webtelemetry.impl.RealTimeProvider
import com.duckduckgo.webtelemetry.impl.StaggerProvider
import com.duckduckgo.webtelemetry.impl.TimeProvider
import com.duckduckgo.webtelemetry.store.ALL_MIGRATIONS
import com.duckduckgo.webtelemetry.store.RealWebTelemetryRepository
import com.duckduckgo.webtelemetry.store.WebTelemetryDatabase
import com.duckduckgo.webtelemetry.store.WebTelemetryRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object WebTelemetryModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideWebTelemetryDatabase(context: Context): WebTelemetryDatabase {
        return Room.databaseBuilder(context, WebTelemetryDatabase::class.java, "web_telemetry.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideWebTelemetryRepository(
        database: WebTelemetryDatabase,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        @IsMainProcess isMainProcess: Boolean,
    ): WebTelemetryRepository {
        return RealWebTelemetryRepository(database, appCoroutineScope, dispatcherProvider, isMainProcess)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideTimeProvider(): TimeProvider = RealTimeProvider()

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideStaggerProvider(): StaggerProvider = RealStaggerProvider()

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideEventHubPixelManager(
        repository: WebTelemetryRepository,
        pixel: Pixel,
        timeProvider: TimeProvider,
        staggerProvider: StaggerProvider,
    ): EventHubPixelManager {
        return RealEventHubPixelManager(repository, pixel, timeProvider, staggerProvider)
    }
}
