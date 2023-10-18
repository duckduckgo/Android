/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.savedsites.impl.di

import android.content.Context
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.service.SavedSitesExporter
import com.duckduckgo.savedsites.api.service.SavedSitesImporter
import com.duckduckgo.savedsites.api.service.SavedSitesManager
import com.duckduckgo.savedsites.impl.RealSavedSitesRepository
import com.duckduckgo.savedsites.impl.service.RealSavedSitesExporter
import com.duckduckgo.savedsites.impl.service.RealSavedSitesImporter
import com.duckduckgo.savedsites.impl.service.RealSavedSitesManager
import com.duckduckgo.savedsites.impl.service.RealSavedSitesParser
import com.duckduckgo.savedsites.impl.service.SavedSitesParser
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import com.duckduckgo.savedsites.store.SavedSitesSettingsSharedPrefStore
import com.duckduckgo.savedsites.store.SavedSitesSettingsStore
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
class SavedSitesModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun savedSitesImporter(
        context: Context,
        savedSitesEntitiesDao: SavedSitesEntitiesDao,
        savedSitesRelationsDao: SavedSitesRelationsDao,
        savedSitesRepository: SavedSitesRepository,
        savedSitesParser: SavedSitesParser,
    ): SavedSitesImporter {
        return RealSavedSitesImporter(context.contentResolver, savedSitesEntitiesDao, savedSitesRelationsDao, savedSitesRepository, savedSitesParser)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun savedSitesParser(): SavedSitesParser {
        return RealSavedSitesParser()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun savedSitesExporter(
        context: Context,
        savedSitesParser: SavedSitesParser,
        savedSitesRepository: SavedSitesRepository,
        dispatcherProvider: DispatcherProvider,
    ): SavedSitesExporter {
        return RealSavedSitesExporter(context.contentResolver, savedSitesRepository, savedSitesParser, dispatcherProvider)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun bookmarkManager(
        savedSitesImporter: SavedSitesImporter,
        savedSitesExporter: SavedSitesExporter,
        pixel: Pixel,
    ): SavedSitesManager {
        return RealSavedSitesManager(savedSitesImporter, savedSitesExporter, pixel)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesSavedSitesRepository(
        savedSitesEntitiesDao: SavedSitesEntitiesDao,
        savedSitesRelationsDao: SavedSitesRelationsDao,
        coroutineDispatcher: DispatcherProvider = DefaultDispatcherProvider(),
    ): SavedSitesRepository {
        return RealSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao, coroutineDispatcher)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesSavedSitesSettingsStore(
        context: Context,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
    ): SavedSitesSettingsStore {
        return SavedSitesSettingsSharedPrefStore(context, appCoroutineScope, dispatcherProvider)
    }
}
