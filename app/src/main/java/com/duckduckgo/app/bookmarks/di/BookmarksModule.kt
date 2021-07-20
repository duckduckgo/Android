/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.bookmarks.di

import android.content.Context
import com.duckduckgo.app.bookmarks.db.BookmarkFoldersDao
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.service.SavedSitesManager
import com.duckduckgo.app.bookmarks.service.SavedSitesExporter
import com.duckduckgo.app.bookmarks.service.SavedSitesImporter
import com.duckduckgo.app.bookmarks.service.SavedSitesParser
import com.duckduckgo.app.bookmarks.service.RealSavedSitesManager
import com.duckduckgo.app.bookmarks.service.RealSavedSitesExporter
import com.duckduckgo.app.bookmarks.service.RealSavedSitesImporter
import com.duckduckgo.app.bookmarks.service.RealSavedSitesParser
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.bookmarks.db.FavoritesDao
import com.duckduckgo.app.bookmarks.model.BookmarkFoldersDataRepository
import com.duckduckgo.app.bookmarks.model.BookmarkFoldersRepository
import com.duckduckgo.app.bookmarks.model.FavoritesDataRepository
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@ContributesTo(AppObjectGraph::class)
class BookmarksModule {

    @Provides
    @Singleton
    fun savedSitesImporter(
        context: Context,
        bookmarksDao: BookmarksDao,
        favoritesRepository: FavoritesRepository,
        savedSitesParser: SavedSitesParser,
    ): SavedSitesImporter {
        return RealSavedSitesImporter(context.contentResolver, bookmarksDao, favoritesRepository, savedSitesParser)
    }

    @Provides
    @Singleton
    fun savedSitesParser(): SavedSitesParser {
        return RealSavedSitesParser()
    }

    @Provides
    @Singleton
    fun savedSitesExporter(
        context: Context,
        savedSitesParser: SavedSitesParser,
        bookmarksDao: BookmarksDao,
        favoritesRepository: FavoritesRepository,
        dispatcherProvider: DispatcherProvider
    ): SavedSitesExporter {
        return RealSavedSitesExporter(context.contentResolver, bookmarksDao, favoritesRepository, savedSitesParser, dispatcherProvider)
    }

    @Provides
    @Singleton
    fun bookmarkManager(
        savedSitesImporter: SavedSitesImporter,
        savedSitesExporter: SavedSitesExporter,
        pixel: Pixel
    ): SavedSitesManager {
        return RealSavedSitesManager(savedSitesImporter, savedSitesExporter, pixel)
    }

    @Provides
    @Singleton
    fun favoriteRepository(favoritesDao: FavoritesDao, faviconManager: Lazy<FaviconManager>): FavoritesRepository {
        return FavoritesDataRepository(favoritesDao, faviconManager)
    }

    @Provides
    @Singleton
    fun bookmarkFoldersRepository(bookmarkFoldersDao: BookmarkFoldersDao, bookmarksDao: BookmarksDao, appDatabase: AppDatabase): BookmarkFoldersRepository {
        return BookmarkFoldersDataRepository(bookmarkFoldersDao, bookmarksDao, appDatabase)
    }
}
