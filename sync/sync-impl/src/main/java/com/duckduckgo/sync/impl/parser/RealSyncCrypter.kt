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

import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.store.SyncStore

class RealSyncCrypter(
    private val repository: SavedSitesRepository,
    private val nativeLib: SyncLib,
    private val syncStore: SyncStore,
) : SyncCrypter {

    @WorkerThread
    override fun generateAllData(): SyncDataRequest {
        val updates = mutableListOf<SyncBookmarkEntry>()

        val hasFavorites = repository.hasFavorites()
        val hasBookmarks = repository.hasBookmarks()

        if (!hasFavorites && !hasBookmarks) {
            return SyncDataRequest("", SyncBookmarkUpdates(emptyList()))
        }

        val primaryKey = syncStore.primaryKey ?: return SyncDataRequest("", SyncBookmarkUpdates(emptyList()))

        // favorites (we don't add individual items, they are added as we go through bookmark folders)
        if (hasFavorites) {
            val favorites = repository.getFavoritesSync()
            updates.add(
                SyncBookmarkEntry.asFolder(
                    id = SavedSitesNames.FAVORITES_ROOT,
                    title = encrypt(SavedSitesNames.FAVORITES_NAME, primaryKey),
                    children = favorites.map { it.id },
                    deleted = null,
                    clientLastModified = DatabaseDateFormatter.iso8601()
                ),
            )
        }

        val bookmarks = addFolderContent(SavedSitesNames.BOOMARKS_ROOT, updates, primaryKey)

        val bookmarkUpdates = SyncBookmarks(SyncBookmarkUpdates(bookmarks))
        return SyncDataRequest("", bookmarkUpdates.bookmarks)
    }

    private fun addFolderContent(
        folderId: String,
        updates: MutableList<SyncBookmarkEntry>,
        primaryKey: String,
    ): List<SyncBookmarkEntry> {
        repository.getFolderContentSync(folderId).apply {
            val folder = repository.getFolder(folderId)
            if (folder != null) {
                val childrenIds = mutableListOf<String>()
                for (bookmark in this.first) {
                    childrenIds.add(bookmark.id)
                    updates.add(encryptSavedSite(bookmark, primaryKey = primaryKey))
                }
                updates.add(encryptFolder(folder, childrenIds, primaryKey = primaryKey))
                for (folder in this.second) {
                    addFolderContent(folder.id, updates, primaryKey)
                }
            }
        }
        return updates
    }

    @WorkerThread
    override fun store(entries: List<SyncBookmarkEntry>): Boolean {
        val primaryKey = syncStore.primaryKey ?: return false

        val folders = entries.filter { it.folder != null }

        folders.forEach { folder ->
            val bookmarkFolder = decryptFolder(folder, primaryKey, folder.id)
            if (bookmarkFolder.id != SavedSitesNames.BOOMARKS_ROOT && bookmarkFolder.id != SavedSitesNames.FAVORITES_ROOT) {
                repository.insert(bookmarkFolder)
            }

            // then subfolders
            folder.folder!!.children.forEach { childId ->
                val entry = entries.find { it.id == childId }
                if (entry != null) {
                    if (entry.isFolder()) {
                        repository.insert(decryptFolder(entry, primaryKey, folder.id))
                    }
                    if (entry.isBookmark()) {
                        repository.insert(decryptBookmark(entry, primaryKey, folder.id))
                    }
                }
            }
        }
        return true
    }

    override fun encrypt(text: String): String {
        TODO("Not yet implemented")
    }

    override fun decrypt(data: String): String {
        TODO("Not yet implemented")
    }

    private fun encryptFolder(
        bookmarkFolder: BookmarkFolder,
        children: List<String>,
        primaryKey: String,
    ): SyncBookmarkEntry {
        return SyncBookmarkEntry.asFolder(
            id = bookmarkFolder.id,
            title = encrypt(bookmarkFolder.name, primaryKey),
            children = children,
            deleted = null,
            clientLastModified = DatabaseDateFormatter.iso8601()
        )
    }

    private fun encrypt(
        id: String,
        primaryKey: String,
    ): String {
        val encryptResult = nativeLib.encryptData(id, primaryKey)
        return if (encryptResult.result != 0L) "" else encryptResult.encryptedData
    }

    private fun encryptSavedSite(
        savedSite: SavedSite,
        primaryKey: String,
    ): SyncBookmarkEntry {
        return SyncBookmarkEntry.asBookmark(
            id = savedSite.id,
            title = encrypt(savedSite.title, primaryKey),
            url = encrypt(savedSite.url, primaryKey),
            deleted = null,
            clientLastModified = DatabaseDateFormatter.iso8601()
        )
    }

    private fun decryptBookmark(
        entry: SyncBookmarkEntry,
        primaryKey: String,
        parentId: String,
    ): Bookmark {
        return Bookmark(
            id = entry.id,
            title = decrypt(entry.title, primaryKey),
            url = decrypt(entry.page!!.url, primaryKey),
            parentId = parentId,
            lastModified = "",
        )
    }

    private fun decryptFolder(
        entry: SyncBookmarkEntry,
        primaryKey: String,
        parentId: String,
    ): BookmarkFolder {
        return BookmarkFolder(id = entry.id, name = decrypt(entry.title, primaryKey), parentId = parentId, 0, 0, lastModified = "")
    }

    private fun decrypt(
        text: String,
        primaryKey: String,
    ): String {
        val decryptResult = nativeLib.decryptData(text, primaryKey)
        return if (decryptResult.result != 0L) "" else decryptResult.decryptedData
    }
}
