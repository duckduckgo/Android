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

import com.duckduckgo.app.bookmarks.model.*
import com.duckduckgo.sync.store.Relation
import java.util.UUID
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

interface SavedSitesParser {
    fun generateHtml(
        folderTree: FolderTree,
        favorites: List<SavedSite.Favorite>,
    ): String

    suspend fun parseHtml(
        document: Document,
        savedSitesRepository: SavedSitesRepository,
    ): List<SavedSite>
}

class RealSavedSitesParser : SavedSitesParser {

    companion object {
        const val FAVORITES_FOLDER = "DuckDuckGo Favorites"
        const val BOOKMARKS_FOLDER = "DuckDuckGo Bookmarks"
    }

    override fun generateHtml(
        folderTree: FolderTree,
        favorites: List<SavedSite.Favorite>,
    ): String {
        if (folderTree.isEmpty() && favorites.isEmpty()) {
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
            append(addFoldersAndBookmarks(folderTree))
            append(addFavorites(favorites))
            appendLine("</DL><p>")
        }
    }

    private fun addFoldersAndBookmarks(folderTree: FolderTree): String {
        return buildString {
            folderTree.forEachVisit(
                { node ->
                    if (node.value.url == null) {
                        if (node.value.depth == 0) {
                            appendLine(
                                "    <DT><H3 ADD_DATE=\"1618844074\" " +
                                    "LAST_MODIFIED=\"1618844074\" PERSONAL_TOOLBAR_FOLDER=\"true\">${node.value.name}</H3>",
                            )
                        } else {
                            appendLine(
                                getTabString(node.value.depth) + "    <DT><H3 ADD_DATE=\"1618844074\" " +
                                    "LAST_MODIFIED=\"1618844074\">${node.value.name}</H3>",
                            )
                        }
                        appendLine(getTabString(node.value.depth) + "    <DL><p>")
                    } else {
                        appendLine(
                            getTabString(node.value.depth) + "    <DT><A HREF=\"${node.value.url}\" ADD_DATE=\"1618844074\" " +
                                "LAST_MODIFIED=\"1618844074\">${node.value.name}</A>",
                        )
                    }
                },
                { node ->
                    if (node.value.url == null) {
                        appendLine(getTabString(node.value.depth) + "    </DL><p>")
                    }
                },
            )
        }
    }

    private fun getTabString(multiplier: Int): String {
        var tabString = ""
        repeat(multiplier) {
            tabString += "    "
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
        savedSitesRepository: SavedSitesRepository,
    ): List<SavedSite> {
        val body = document.select("body").first() ?: return emptyList()
        val children = body.childNodes()
            .filterIsInstance<Element>()
            .filter { it.select("DT").isNotEmpty() }

        var rootElement: Element = document

        if (children.size > 1) {
            rootElement = Element("DL").appendChildren(children)
        }
        return parseElement(rootElement, "", savedSitesRepository, mutableListOf(), false)
    }

    private suspend fun parseElement(
        documentElement: Element,
        parentId: String,
        savedSitesRepository: SavedSitesRepository,
        savedSites: MutableList<SavedSite>,
        inFavorite: Boolean,
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

                        if (folderName == FAVORITES_FOLDER || folderName == BOOKMARKS_FOLDER) {
                            parseElement(element, Relation.BOOMARKS_ROOT, savedSitesRepository, savedSites, folderName == FAVORITES_FOLDER)
                        } else {
                            val bookmarkFolder = BookmarkFolder(name = folderName, parentId = parentId, id = UUID.randomUUID().toString())
                            savedSitesRepository.insert(bookmarkFolder)
                            val folder = savedSitesRepository.getFolder(bookmarkFolder.id)
                            parseElement(element, folder.id, savedSitesRepository, savedSites, false)
                        }
                    } else {
                        val linkItem = element.select("a")
                        if (linkItem.isNotEmpty()) {
                            val link = linkItem.attr("href")
                            val title = linkItem.text()
                            if (inFavorite) {
                                savedSites.add(SavedSite.Favorite(UUID.randomUUID().toString(), title = title, url = link, favorites))
                                favorites++
                            } else {
                                val bookmark = SavedSite.Bookmark(UUID.randomUUID().toString(), title = title, url = link, parentId = parentId)
                                savedSites.add(bookmark)
                            }
                        }
                    }
                }
        }
        return savedSites
    }
}
