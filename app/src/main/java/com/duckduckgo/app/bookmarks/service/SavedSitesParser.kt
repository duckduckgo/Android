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

import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.model.BookmarkFolderItem
import com.duckduckgo.app.bookmarks.model.BookmarkFoldersRepository
import com.duckduckgo.app.bookmarks.model.SavedSite
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

interface SavedSitesParser {
    fun generateHtml(bookmarks: List<SavedSite.Bookmark>, bookmarkFolderItems: List<BookmarkFolderItem>, favorites: List<SavedSite.Favorite>): String
    suspend fun parseHtml(document: Document, bookmarkFoldersRepository: BookmarkFoldersRepository): List<SavedSite>
}

class RealSavedSitesParser : SavedSitesParser {

    companion object {
        const val FAVORITES_FOLDER = "DuckDuckGo Favorites"
        const val BOOKSMARKS_FOLDER = "DuckDuckGo Bookmarks"
    }

    override fun generateHtml(bookmarks: List<SavedSite.Bookmark>, bookmarkFolderItems: List<BookmarkFolderItem>, favorites: List<SavedSite.Favorite>): String {
        if (bookmarkFolderItems.isEmpty() && bookmarks.isEmpty() && favorites.isEmpty()) {
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
            append(addBookmarksAndFolders(bookmarks, bookmarkFolderItems))
            append(addFavorites(favorites))
            appendLine("</DL><p>")
        }
    }

    private fun addBookmarksAndFolders(bookmarks: List<SavedSite.Bookmark>, bookmarkFolderItems: List<BookmarkFolderItem>): String {
        if (bookmarks.isEmpty() && bookmarkFolderItems.isEmpty()) {
            return ""
        }

        return buildString {
            appendLine("    <DT><H3 ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\" PERSONAL_TOOLBAR_FOLDER=\"true\">$BOOKSMARKS_FOLDER</H3>")
            appendLine("    <DL><p>")

            bookmarkFolderItems.forEachIndexed { index, item ->
                appendLine(getTabString(item.depth) + "    <DT><H3 ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\">${item.bookmarkFolder.name}</H3>")
                appendLine(getTabString(item.depth) + "    <DL><p>")

                val nextIndex = index + 1
                var nextDepth = 1

                if (bookmarkFolderItems.size > nextIndex) {
                    nextDepth = bookmarkFolderItems[nextIndex].depth
                }

                if (nextDepth <= item.depth) {
                    val closingDepth = item.depth - nextDepth
                    for (depth in 0..closingDepth) {
                        bookmarkFolderItems[index - depth].bookmarks.forEach { bookmark ->
                            appendLine(getTabString(item.depth - depth + 1) + "    <DT><A HREF=\"${bookmark.url}\" ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\">${bookmark.title}</A>")
                        }
                        appendLine(getTabString(item.depth - depth) + "    </DL><p>")
                    }
                }
            }
            bookmarks.filter { it.parentId == 0L }.forEach { entity ->
                appendLine("        <DT><A HREF=\"${entity.url}\" ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\">${entity.title}</A>")
            }
            appendLine("    </DL><p>")
        }
    }

    private fun getTabString(multiplier: Int): String {
        var tabString = ""
        repeat(multiplier) {
            tabString += "\t"
        }
        return tabString
    }

    private fun addFavorites(favorites: List<SavedSite.Favorite>): String {
        if (favorites.isEmpty()) {
            return ""
        }
        return buildString {
            appendLine("    <DT><H3 ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\">$FAVORITES_FOLDER</H3>")
            appendLine("    <DL><p>")
            favorites.forEach { entity ->
                appendLine("        <DT><A HREF=\"${entity.url}\" ADD_DATE=\"1618844074\" LAST_MODIFIED=\"1618844074\">${entity.title}</A>")
            }
            appendLine("    </DL><p>")
        }
    }

    override suspend fun parseHtml(
        document: Document,
        bookmarkFoldersRepository: BookmarkFoldersRepository
    ): List<SavedSite> {
        return parseElement(document, 0, bookmarkFoldersRepository, mutableListOf(), false)
    }

    private suspend fun parseElement(
        documentElement: Element,
        parentId: Long,
        bookmarkFoldersRepository: BookmarkFoldersRepository,
        savedSites: MutableList<SavedSite>,
        inFavorite: Boolean
    ): List<SavedSite> {

        var favorites = 0

        documentElement.select("DL").first()?.let { itemBlock ->

            itemBlock.childNodes()
                .map { it as Element }
                .filter { it.select("DT").isNotEmpty() }
                .forEach { element ->

                    val folder = element.select("H3").first()

                    if (folder != null) {
                        val folderName = folder.text()

                        if (folderName == FAVORITES_FOLDER || folderName == BOOKSMARKS_FOLDER) {
                            parseElement(element, 0, bookmarkFoldersRepository, savedSites, folderName == FAVORITES_FOLDER)
                        } else {
                            val bookmarkFolder = BookmarkFolder(name = folderName, parentId = parentId)
                            val id = bookmarkFoldersRepository.insert(bookmarkFolder)
                            parseElement(element, id, bookmarkFoldersRepository, savedSites, false)
                        }
                    } else {
                        val linkItem = element.select("a")
                        if (linkItem.isNotEmpty()) {
                            val link = linkItem.attr("href")
                            val title = linkItem.text()
                            if (inFavorite) {
                                savedSites.add(SavedSite.Favorite(0, title = title, url = link, favorites))
                                favorites++
                            } else {
                                val bookmark = SavedSite.Bookmark(0, title = title, url = link, parentId = parentId)
                                savedSites.add(bookmark)
                            }
                        }
                    }
                }
        }
        return savedSites
    }
}
