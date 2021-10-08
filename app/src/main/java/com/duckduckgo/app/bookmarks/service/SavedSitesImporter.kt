package com.duckduckgo.app.bookmarks.service

import android.content.ContentResolver
import android.net.Uri
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.model.BookmarksRepository
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.bookmarks.model.SavedSite
import org.jsoup.Jsoup

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

interface SavedSitesImporter {
    suspend fun import(uri: Uri): ImportSavedSitesResult
}

sealed class ImportSavedSitesResult {
    data class Success(val savedSites: List<SavedSite>) : ImportSavedSitesResult()
    data class Error(val exception: Exception) : ImportSavedSitesResult()
}

class RealSavedSitesImporter(
    private val contentResolver: ContentResolver,
    private val bookmarksDao: BookmarksDao,
    private val favoritesRepository: FavoritesRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val savedSitesParser: SavedSitesParser
) : SavedSitesImporter {

    companion object {
        private const val BASE_URI = "duckduckgo.com"
    }

    override suspend fun import(uri: Uri): ImportSavedSitesResult {
        return try {
            val savedSites = contentResolver.openInputStream(uri).use { stream ->
                val document = Jsoup.parse(stream, Charsets.UTF_8.name(), BASE_URI)
                savedSitesParser.parseHtml(document, bookmarksRepository)
            }

            savedSites.forEach {
                when (it) {
                    is SavedSite.Favorite -> favoritesRepository.insert(it)
                    is SavedSite.Bookmark -> bookmarksDao.insert(BookmarkEntity(title = it.title, url = it.url, parentId = it.parentId))
                }
            }
            ImportSavedSitesResult.Success(savedSites)
        } catch (exception: Exception) {
            ImportSavedSitesResult.Error(exception)
        }
    }
}
