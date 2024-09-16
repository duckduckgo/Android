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
import androidx.room.Room
import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.app.di.*
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.promotion.BookmarksScreenPromotionPlugin
import com.duckduckgo.savedsites.api.service.SavedSitesExporter
import com.duckduckgo.savedsites.api.service.SavedSitesImporter
import com.duckduckgo.savedsites.api.service.SavedSitesManager
import com.duckduckgo.savedsites.impl.*
import com.duckduckgo.savedsites.impl.service.RealSavedSitesExporter
import com.duckduckgo.savedsites.impl.service.RealSavedSitesImporter
import com.duckduckgo.savedsites.impl.service.RealSavedSitesManager
import com.duckduckgo.savedsites.impl.service.RealSavedSitesParser
import com.duckduckgo.savedsites.impl.service.SavedSitesParser
import com.duckduckgo.savedsites.impl.sync.*
import com.duckduckgo.savedsites.impl.sync.store.ALL_MIGRATIONS
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncEntitiesStore
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDao
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDatabase
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import com.duckduckgo.savedsites.store.SavedSitesSettingsSharedPrefStore
import com.duckduckgo.savedsites.store.SavedSitesSettingsStore
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.*

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
        favoritesDelegate: FavoritesDelegate,
        relationsReconciler: RelationsReconciler,
        coroutineDispatcher: DispatcherProvider = DefaultDispatcherProvider(),
    ): SavedSitesRepository {
        return RealSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao, favoritesDelegate, relationsReconciler, coroutineDispatcher)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesSyncSavedSitesRepository(
        savedSitesEntitiesDao: SavedSitesEntitiesDao,
        savedSitesRelationsDao: SavedSitesRelationsDao,
        savedSitesSyncMetadataDao: SavedSitesSyncMetadataDao,
        savedSitesSyncEntitiesStore: SavedSitesSyncEntitiesStore,
    ): SyncSavedSitesRepository {
        return RealSyncSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao, savedSitesSyncMetadataDao, savedSitesSyncEntitiesStore)
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

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideSavedSitesDatabase(context: Context): SavedSitesSyncMetadataDatabase {
        return Room.databaseBuilder(context, SavedSitesSyncMetadataDatabase::class.java, "saved_sites_metadata.db")
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideSavedSitesSyncMetadataDao(database: SavedSitesSyncMetadataDatabase): SavedSitesSyncMetadataDao {
        return database.syncMetadataDao()
    }
}

@ContributesPluginPoint(scope = ActivityScope::class, boundType = BookmarksScreenPromotionPlugin::class)
private interface BookmarksScreenPromotionPluginPoint
