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
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

interface BookmarksExporter {
    suspend fun export(uri: Uri): ExportBookmarksResult
}

sealed class ExportBookmarksResult {
    object Success : ExportBookmarksResult()
    data class Error(val exception: Exception) : ExportBookmarksResult()
    object NoBookmarksExported : ExportBookmarksResult()
}

class RealBookmarksExporter(
    private val contentResolver: ContentResolver,
    private val bookmarksDao: BookmarksDao,
    private val bookmarksParser: BookmarksParser,
    private val dispatcher: DispatcherProvider = DefaultDispatcherProvider()
) : BookmarksExporter {

    override suspend fun export(uri: Uri): ExportBookmarksResult {
        val bookmarks = withContext(dispatcher.io()) {
            bookmarksDao.getBookmarksSync()
        }

        val html = bookmarksParser.generateHtml(bookmarks)
        return storeHtml(uri, html)
    }

    private fun storeHtml(uri: Uri, content: String): ExportBookmarksResult {
        return try {
            if (content.isEmpty()) {
                return ExportBookmarksResult.NoBookmarksExported
            }
            val file = contentResolver.openFileDescriptor(uri, "w")
            if (file != null) {
                val fileOutputStream = FileOutputStream(file.fileDescriptor)
                fileOutputStream.write(content.toByteArray())
                fileOutputStream.close()
                file.close()
                ExportBookmarksResult.Success
            } else {
                ExportBookmarksResult.NoBookmarksExported
            }
        } catch (e: FileNotFoundException) {
            ExportBookmarksResult.Error(e)
        } catch (e: IOException) {
            ExportBookmarksResult.Error(e)
        }
    }

}
