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

import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

interface BookmarksParser {
    fun generateHtml(bookmarks: List<BookmarkEntity>): String
    fun parseHtml(document: Document): List<Bookmark>
    fun parseHtml(html: String): List<Bookmark>
}

class DuckDuckGoBookmarksParser() : BookmarksParser {

    override fun generateHtml(bookmarks: List<BookmarkEntity>): String {
        if (bookmarks.isEmpty()) {
            return ""
        }

        return buildString {
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
            bookmarks.forEach { entity ->
                appendLine("        <DT><A HREF=\"${entity.url}\" ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\">${entity.title}</A>")
            }
            appendLine("    </DL><p>")
            appendLine("</DL><p>")
        }
    }

    override fun parseHtml(document: Document): List<Bookmark> {
        val validBookmarks = mutableListOf<Bookmark>()
        val fileItems = document.select("DT")

        fileItems.forEach {
            val folderItem = it.select("H3")
            if (folderItem.isNotEmpty()) {
                // when folders are implemented this is what we need to use
                return@forEach
            }

            val linkItem = it.select("a")
            if (linkItem.isNotEmpty()) {
                val link = linkItem.attr("href")
                val title = linkItem.text()
                val bookmark = Bookmark(title, link)
                validBookmarks.add(bookmark)
            }
        }

        return validBookmarks
    }

    @VisibleForTesting
    override fun parseHtml(html: String): List<Bookmark> {
        val document = Jsoup.parse(html)
        return parseHtml(document)
    }
}
