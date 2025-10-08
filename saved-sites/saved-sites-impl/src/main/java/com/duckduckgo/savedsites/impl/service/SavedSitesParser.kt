/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.savedsites.impl.service

import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.api.service.SavedSitesImporter.ImportFolder
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.UUID

interface SavedSitesParser {
    fun generateHtml(
        folderTree: FolderTree,
        favorites: List<SavedSite.Favorite>,
    ): String

    suspend fun parseHtml(
        document: Document,
        savedSitesRepository: SavedSitesRepository,
        destination: ImportFolder,
    ): List<Any>
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
        destination: ImportFolder,
    ): List<Any> {
        val body = document.select("body").first() ?: return emptyList()
        val children = body.childNodes()
            .filterIsInstance<Element>()
            .filter { it.select("DT").isNotEmpty() }

        var rootElement: Element = document

        if (children.size > 1) {
            rootElement = Element("DL").appendChildren(children)
        }

        val destinationFolderId = when (destination) {
            is ImportFolder.Root -> SavedSitesNames.BOOKMARKS_ROOT
            is ImportFolder.Folder -> {
                // Check if folder with this name already exists, otherwise create it
                val existingFolder = savedSitesRepository.getFolderTreeItems(SavedSitesNames.BOOKMARKS_ROOT)
                    .find { it.url == null && it.name == destination.folderName && it.parentId == SavedSitesNames.BOOKMARKS_ROOT }

                existingFolder?.id ?: run {
                    // Create new folder if it doesn't exist
                    val newFolder = savedSitesRepository.insert(
                        BookmarkFolder(
                            id = UUID.randomUUID().toString(),
                            name = destination.folderName,
                            parentId = SavedSitesNames.BOOKMARKS_ROOT,
                            lastModified = DatabaseDateFormatter.iso8601(),
                        ),
                    )
                    newFolder.id
                }
            }
        }

        return parseElement(rootElement, destinationFolderId, savedSitesRepository, mutableListOf(), false)
    }

    private fun parseElement(
        documentElement: Element,
        parentId: String,
        savedSitesRepository: SavedSitesRepository,
        savedSites: MutableList<Any>,
        inFavorite: Boolean,
    ): List<Any> {
        var favorites = 0

        documentElement.select("DL").first()?.let { itemBlock ->

            itemBlock.childNodes()
                .map { it as Element }
                .filter { it.select("DT").isNotEmpty() }
                .forEach { element ->

                    val folder = element.select("H3").first()

                    if (folder != null) {
                        val folderName = folder.text()

                        if (isFavoritesFolder(folderName) || isBookmarksFolder(folderName)) {
                            parseElement(element, SavedSitesNames.BOOKMARKS_ROOT, savedSitesRepository, savedSites, folderName == FAVORITES_FOLDER)
                        } else {
                            val bookmarkFolder = BookmarkFolder(
                                name = folderName,
                                parentId = parentId,
                                lastModified = DatabaseDateFormatter.iso8601(),
                                deleted = null,
                            )
                            // if folder exists we use that one instead
                            val existingFolder = savedSitesRepository.getFolderByName(folderName)
                            if (existingFolder != null) {
                                parseElement(element, existingFolder.id, savedSitesRepository, savedSites, false)
                            } else {
                                savedSites.add(bookmarkFolder)
                                parseElement(element, bookmarkFolder.id, savedSitesRepository, savedSites, false)
                            }
                        }
                    } else {
                        val linkItem = element.select("a")
                        if (linkItem.isNotEmpty()) {
                            val link = linkItem.attr("href")
                            val title = linkItem.text()
                            if (inFavorite) {
                                savedSites.add(
                                    SavedSite.Favorite(
                                        UUID.randomUUID().toString(),
                                        title = title,
                                        url = link,
                                        lastModified = DatabaseDateFormatter.iso8601(),
                                        favorites,
                                    ),
                                )
                                favorites++
                            } else {
                                val bookmark = SavedSite.Bookmark(
                                    UUID.randomUUID().toString(),
                                    title = title,
                                    url = link,
                                    parentId = parentId,
                                    lastModified = DatabaseDateFormatter.iso8601(),
                                )
                                savedSites.add(bookmark)
                            }
                        }
                    }
                }
        }
        return savedSites
    }

    private fun isFavoritesFolder(folderName: String): Boolean {
        return folderName == FAVORITES_FOLDER || folderName == SavedSitesNames.FAVORITES_NAME
    }

    private fun isBookmarksFolder(folderName: String): Boolean {
        return folderName == BOOKMARKS_FOLDER || folderName == SavedSitesNames.BOOKMARKS_NAME
    }
}
