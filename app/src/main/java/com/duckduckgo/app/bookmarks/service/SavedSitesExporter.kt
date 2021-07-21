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

package com.duckduckgo.app.bookmarks.service

import android.content.ContentResolver
import android.net.Uri
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

interface SavedSitesExporter {
    suspend fun export(uri: Uri): ExportSavedSitesResult
}

sealed class ExportSavedSitesResult {
    object Success : ExportSavedSitesResult()
    data class Error(val exception: Exception) : ExportSavedSitesResult()
    object NoSavedSitesExported : ExportSavedSitesResult()
}

class RealSavedSitesExporter(
    private val contentResolver: ContentResolver,
    private val bookmarksDao: BookmarksDao,
    private val favoritesRepository: FavoritesRepository,
    private val savedSitesParser: SavedSitesParser,
    private val dispatcher: DispatcherProvider = DefaultDispatcherProvider()
) : SavedSitesExporter {

    override suspend fun export(uri: Uri): ExportSavedSitesResult {
        val bookmarks = withContext(dispatcher.io()) {
            bookmarksDao.getBookmarksSync()
        }
        val favorites = withContext(dispatcher.io()) {
            favoritesRepository.favoritesSync()
        }

        val html = savedSitesParser.generateHtml(bookmarks, favorites)
        return storeHtml(uri, html)
    }

    private fun storeHtml(uri: Uri, content: String): ExportSavedSitesResult {
        return try {
            if (content.isEmpty()) {
                return ExportSavedSitesResult.NoSavedSitesExported
            }
            val file = contentResolver.openFileDescriptor(uri, "w")
            if (file != null) {
                val fileOutputStream = FileOutputStream(file.fileDescriptor)
                fileOutputStream.write(content.toByteArray())
                fileOutputStream.close()
                file.close()
                ExportSavedSitesResult.Success
            } else {
                ExportSavedSitesResult.NoSavedSitesExported
            }
        } catch (e: FileNotFoundException) {
            ExportSavedSitesResult.Error(e)
        } catch (e: IOException) {
            ExportSavedSitesResult.Error(e)
        }
    }

}
