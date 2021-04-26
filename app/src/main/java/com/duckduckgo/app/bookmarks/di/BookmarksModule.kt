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
import com.duckduckgo.app.bookmarks.service.*
import com.duckduckgo.app.global.DispatcherProvider
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class BookmarksModule {

    @Provides
    @Singleton
    fun bookmarksImporter(
        context: Context,
        bookmarksDao: BookmarksDao
    ): BookmarksImporter {
        return DuckDuckGoBookmarksImporter(context.contentResolver, bookmarksDao)
    }

    @Provides
    @Singleton
    fun bookmarksExporter(
        context: Context,
        bookmarksDao: BookmarksDao,
        dispatcherProvider: DispatcherProvider
    ): BookmarksExporter {
        return DuckDuckGoBookmarksExporter(context.contentResolver, bookmarksDao, dispatcherProvider)
    }

    @Provides
    @Singleton
    fun bookmarkManager(
        bookmarksImporter: BookmarksImporter,
        bookmarksExporter: BookmarksExporter
    ): BookmarkManager {
        return DuckDuckGoBookmarkManager(bookmarksImporter, bookmarksExporter)
    }

}