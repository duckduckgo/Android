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

package com.duckduckgo.sync.impl.parser

import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.store.Relation
import com.duckduckgo.sync.api.parser.SyncDataBookmarks
import com.duckduckgo.sync.api.parser.SyncCrypter
import com.duckduckgo.sync.api.parser.SyncDataUpdates
import com.duckduckgo.sync.api.parser.SyncEntry
import com.duckduckgo.sync.api.parser.isBookmark
import com.duckduckgo.sync.api.parser.isFolder
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.withContext

class RealSyncCrypter(
    private val repository: SavedSitesRepository,
    private val nativeLib: SyncLib,
    private val syncStore: SyncStore,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : SyncCrypter {

    override suspend fun generateAllData(): SyncDataBookmarks {
        val allData = withContext(dispatcherProvider.io()) {
            val updates = mutableListOf<SyncEntry>()

            val hasFavorites = repository.hasFavorites()
            val hasBookmarks = repository.hasBookmarks()

            if (!hasFavorites && !hasBookmarks) {
                return@withContext SyncDataBookmarks(SyncDataUpdates(emptyList()))
            }

            // favorites (we don't add individual items, they are added as we go through bookmark folders
            if (hasFavorites) {
                val favorites = repository.getFavoritesSync()
                updates.add(
                    SyncEntry.asFolder(
                        id = Relation.FAVORITES_ROOT,
                        title = "Favorites",
                        children = favorites.map { it.id },
                        deleted = null,
                    ),
                )
            }

            // for now, only bookmarks
            val bookmarks = addFolderContent(Relation.BOOMARKS_ROOT, updates)

            val bookmarkUpdates = SyncDataUpdates(bookmarks)
            SyncDataBookmarks(bookmarkUpdates)
        }
        return allData
    }

    private suspend fun addFolderContent(
        folderId: String,
        updates: MutableList<SyncEntry>
    ): List<SyncEntry> {
        repository.getFolderContentSync(folderId).apply {
            val folder = repository.getFolder(folderId)
            if (folder != null) {
                val childrenIds = mutableListOf<String>()
                for (bookmark in this.first) {
                    childrenIds.add(bookmark.id)
                    updates.add(SyncEntry.asBookmark(id = bookmark.id, title = bookmark.title, url = bookmark.url, deleted = null))
                }
                updates.add(SyncEntry.asFolder(id = folder.id, title = folder.name, children = childrenIds, deleted = null))
                for (folder in this.second) {
                    addFolderContent(folder.id, updates)
                }
            }
        }
        return updates
    }

    override fun store(entries: List<SyncEntry>): Boolean {
        val primaryKey = syncStore.primaryKey ?: return false

        val folders = entries.filter { it.folder != null }

        folders.forEach { folder ->
            folder.folder!!.children.forEach { childId ->
                val entry = entries.find { it.id == childId }
                if (entry != null) {
                    if (entry.isFolder()) {
                        val bookmarkFolder = decryptFolder(entry, primaryKey, folder.id)
                        repository.insert(bookmarkFolder)
                    }
                    if (entry.isBookmark()) {
                        val bookmark = decryptBookmark(entry, primaryKey, folder.id)
                        repository.insert(bookmark)
                    }
                }
            }
        }
        return true
    }

    private fun decryptBookmark(
        entry: SyncEntry,
        primaryKey: String,
        parentId: String
    ): Bookmark {
        val idDecryptResult = nativeLib.decrypt(entry.id, primaryKey)
        val id = if (idDecryptResult.result != 0L) "" else idDecryptResult.decryptedData

        val urlDecryptResult = nativeLib.decrypt(entry.page!!.url, primaryKey)
        val url = if (urlDecryptResult.result != 0L) "" else urlDecryptResult.decryptedData

        return Bookmark(id = id, title = entry.title, url = url, parentId = parentId)
    }

    private fun decryptFolder(
        entry: SyncEntry,
        primaryKey: String,
        parentId: String
    ): BookmarkFolder {
        val idDecryptResult = nativeLib.decrypt(entry.id, primaryKey)
        val id = if (idDecryptResult.result != 0L) "" else idDecryptResult.decryptedData

        return BookmarkFolder(id = id, name = entry.title, parentId = parentId, 0, 0)
    }
}
