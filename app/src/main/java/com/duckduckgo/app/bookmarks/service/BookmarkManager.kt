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
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

data class Bookmark(val title: String, val url: String)

sealed class ExportBookmarksResult {
    object Success : ExportBookmarksResult()
    data class Error(val exception: Exception) : ExportBookmarksResult()
    object NoBookmarksAvailable : ExportBookmarksResult()
}


interface BookmarkManager {
    suspend fun export(uri: Uri): ExportBookmarksResult
    suspend fun importUri(uri: Uri): List<Bookmark>
}

class DuckDuckGoBookmarkManager constructor(
    private val contentResolver: ContentResolver,
    private val dao: BookmarksDao,
    private val dispatcher: DispatcherProvider = DefaultDispatcherProvider()
) : BookmarkManager {


    override suspend fun export(uri: Uri): ExportBookmarksResult {
        return withContext(dispatcher.io()) {
            if (dao.hasBookmarks()) {
               val bookmarks =  buildString {
                    appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
                    appendLine("<!--This is an automatically generated file.")
                    appendLine("It will be read and overwritten.")
                    appendLine("Do Not Edit! -->")
                    appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
                    appendLine("<Title>Bookmarks</Title>")
                    appendLine("<H1>Bookmarks</H1>")
                    appendLine("<DL><p>")
                    appendLine("    <DT><H3 ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\" PERSONAL_TOOLBAR_FOLDER=\"true\">DuckDuckGo</H3>")
                    appendLine("    <DL><p>")
                    dao.bookmarksSync().forEach { entity ->
                        appendLine("        <DT><A HREF=\"${entity.url}\" ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\">${entity.title}</A>")
                    }
                    appendLine("    </DL><p>")
                    appendLine("</DL><p>")
                }
                writeFileContent(uri, bookmarks)
            } else {
                ExportBookmarksResult.NoBookmarksAvailable
            }
        }
    }

    private fun writeFileContent(uri: Uri, content: String): ExportBookmarksResult {
        return try {
            val file = contentResolver.openFileDescriptor(uri, "w")
            if (file != null) {
                val fileOutputStream = FileOutputStream(file.fileDescriptor)
                fileOutputStream.write(content.toByteArray())
                fileOutputStream.close()
                file.close()
                ExportBookmarksResult.Success
            } else {
                ExportBookmarksResult.NoBookmarksAvailable
            }
        } catch (e: FileNotFoundException) {
            ExportBookmarksResult.Error(e)
        } catch (e: IOException) {
            ExportBookmarksResult.Error(e)
        }
    }

    override suspend fun importUri(uri: Uri): List<Bookmark> {
        val bookmarks = contentResolver.openInputStream(uri).use { stream ->
            val document = Jsoup.parse(stream, "UTF-8", "duckduckgo.com")
            parseDocument(document)
        }
        bookmarks.forEach {
            dao.insert(BookmarkEntity(title = it.title, url = it.url))
        }

        return bookmarks
    }

    fun import(html: String): List<Bookmark> {
        val document = Jsoup.parse(html)
        return parseDocument(document)
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