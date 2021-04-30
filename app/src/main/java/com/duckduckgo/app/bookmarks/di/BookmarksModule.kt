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
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.service.BookmarksManager
import com.duckduckgo.app.bookmarks.service.BookmarksExporter
import com.duckduckgo.app.bookmarks.service.BookmarksImporter
import com.duckduckgo.app.bookmarks.service.BookmarksParser
import com.duckduckgo.app.bookmarks.service.RealBookmarksManager
import com.duckduckgo.app.bookmarks.service.RealBookmarksExporter
import com.duckduckgo.app.bookmarks.service.RealBookmarksImporter
import com.duckduckgo.app.bookmarks.service.RealBookmarksParser
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@ContributesTo(AppObjectGraph::class)
class BookmarksModule {

    @Provides
    @Singleton
    fun bookmarksImporter(
        context: Context,
        bookmarksDao: BookmarksDao,
        bookmarksParser: BookmarksParser,
    ): BookmarksImporter {
        return RealBookmarksImporter(context.contentResolver, bookmarksDao, bookmarksParser)
    }

    @Provides
    @Singleton
    fun bookmarksParser(): BookmarksParser {
        return RealBookmarksParser()
    }

    @Provides
    @Singleton
    fun bookmarksExporter(
        context: Context,
        bookmarksParser: BookmarksParser,
        bookmarksDao: BookmarksDao,
        dispatcherProvider: DispatcherProvider
    ): BookmarksExporter {
        return RealBookmarksExporter(context.contentResolver, bookmarksDao, bookmarksParser, dispatcherProvider)
    }

    @Provides
    @Singleton
    fun bookmarkManager(
        bookmarksImporter: BookmarksImporter,
        bookmarksExporter: BookmarksExporter
    ): BookmarksManager {
        return RealBookmarksManager(bookmarksImporter, bookmarksExporter)
    }
}
