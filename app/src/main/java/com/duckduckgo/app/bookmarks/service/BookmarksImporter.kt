package com.duckduckgo.app.bookmarks.service

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

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

interface BookmarksImporter {
    suspend fun import(uri: Uri): ImportBookmarksResult
    fun import(html: String): ImportBookmarksResult
}

sealed class ImportBookmarksResult {
    data class Success(val bookmarks: List<Bookmark>) : ImportBookmarksResult()
    data class Error(val exception: Exception) : ImportBookmarksResult()
}

class DuckDuckGoBookmarksImporter(
    private val contentResolver: ContentResolver,
    private val dao: BookmarksDao
) : BookmarksImporter {

    companion object {
        private const val CHARSET = "UTF-8"
        private const val BASE_URI = "duckduckgo.com"
    }

    override suspend fun import(uri: Uri): ImportBookmarksResult {
        return try {
            val bookmarks = contentResolver.openInputStream(uri).use { stream ->
                val document = Jsoup.parse(stream, CHARSET, BASE_URI)
                parseDocument(document)
            }

            bookmarks.forEach {
                dao.insert(BookmarkEntity(title = it.title, url = it.url))
            }
            ImportBookmarksResult.Success(bookmarks)
        } catch (exception: Exception) {
            ImportBookmarksResult.Error(exception)
        }
    }

    @VisibleForTesting
    override fun import(html: String): ImportBookmarksResult {
        return try {
            val document = Jsoup.parse(html)
            val bookmarks = parseDocument(document)
            return ImportBookmarksResult.Success(bookmarks)
        } catch (exception: Exception) {
            ImportBookmarksResult.Error(exception)
        }
    }

    private fun parseDocument(document: Document): List<Bookmark> {
        val validBookmarks = mutableListOf<Bookmark>()
        val bookmarkLinks = document.select("a")
        bookmarkLinks.forEach { possibleBookmark ->
            val link = possibleBookmark.attr("href")
            val title = possibleBookmark.text()
            val bookmark = Bookmark(title, link)
            validBookmarks.add(bookmark)
        }
        return validBookmarks
    }
}
