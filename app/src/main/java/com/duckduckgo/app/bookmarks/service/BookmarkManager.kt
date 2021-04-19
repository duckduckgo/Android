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

import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class BookmarkManager(
    private val dao: BookmarksDao,
    private val dispatcher: DispatcherProvider = DefaultDispatcherProvider()
) {

    suspend fun export(): String {
        return withContext(dispatcher.io()) {
            if (dao.hasBookmarks()) {
                buildString {
                    appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
                    appendLine("<!--This is an automatically generated file.")
                    appendLine("It will be read and overwritten.")
                    appendLine("Do Not Edit! -->")
                    appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
                    appendLine("<Title>Bookmarks</Title>")
                    appendLine("<H1>Bookmarks</H1>")
                    appendLine()
                    appendLine("<DL><p>")
                    appendLine("<DT><H3 ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\">DuckDuckGo</H3>")

                    dao.bookmarksSync().forEach { entity ->
                        appendLine("<DT><A HREF=\"${entity.url}\" ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\">${entity.title}</A>")
                    }

                    appendLine("</DL>")
                    appendLine()
                }
            } else {
                ""
            }
        }
    }

    fun import(html: String): Int {
        val document = Jsoup.parse(html)
        val bookmarkLinks = document.select("DL")
        val bookmarkFolder = document.select("DT")
        val title = document.title()
        return 0
    }
}