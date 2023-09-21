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

package com.duckduckgo.savedsites.impl.sync

import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.sync.algorithm.isDeleted
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject
import timber.log.Timber

@ContributesMultibinding(scope = AppScope::class, boundType = SyncableDataProvider::class)
class SavedSitesSyncDataProvider @Inject constructor(
    private val repository: SavedSitesRepository,
    private val savedSitesSyncStore: SavedSitesSyncStore,
    private val syncCrypto: SyncCrypto,
) : SyncableDataProvider {

    override fun getChanges(): SyncChangesRequest {
        savedSitesSyncStore.startTimeStamp = DatabaseDateFormatter.iso8601()
        val updates = if (savedSitesSyncStore.serverModifiedSince == "0") {
            allContent()
        } else {
            changesSince(savedSitesSyncStore.clientModifiedSince)
        }
        return formatUpdates(updates)
    }

    @VisibleForTesting
    fun changesSince(since: String): List<SyncBookmarkEntry> {
        Timber.d("Sync-Bookmarks: generating changes since $since")
        val updates = mutableListOf<SyncBookmarkEntry>()

        // we start adding individual folders that have been modified
        val folders = repository.getFoldersModifiedSince(since)
        folders.forEach { folder ->
            if (folder.isDeleted()) {
                updates.add(deletedEntry(folder.id, folder.deleted!!))
            } else {
                val folderEntry = addFolderEntry(folder.id)
                if (folderEntry != null) {
                    updates.add(folderEntry)
                }
            }
        }

        // then we add individual bookmarks that have been modified
        val bookmarks = repository.getBookmarksModifiedSince(since)
        bookmarks.forEach { bookmark ->
            if (bookmark.isDeleted()) {
                updates.add(deletedEntry(bookmark.id, bookmark.deleted!!))
            } else {
                updates.add(encryptSavedSite(bookmark))
            }
        }

        return updates.distinct()
    }

    @VisibleForTesting
    fun allContent(): List<SyncBookmarkEntry> {
        Timber.d("Sync-Bookmarks: generating all content")
        val hasFavorites = repository.hasFavorites()
        val hasBookmarks = repository.hasBookmarks()

        if (!hasFavorites && !hasBookmarks) {
            Timber.d("Sync-Bookmarks: nothing to generate, favourites and bookmarks empty")
            return emptyList()
        }

        val updates = mutableListOf<SyncBookmarkEntry>()
        // favorites (we don't add individual items, they are added as we go through bookmark folders)
        if (hasFavorites) {
            val favorites = repository.getFavoritesSync()
            val favoriteFolder = repository.getFolder(SavedSitesNames.FAVORITES_ROOT)
            favoriteFolder?.let {
                updates.add(encryptFolder(favoriteFolder, favorites.map { it.id }))
            }
        }

        addFolderEntryWithContent(SavedSitesNames.BOOKMARKS_ROOT, updates)
        return updates.distinct()
    }

    private fun addFolderEntry(folderId: String): SyncBookmarkEntry? {
        val folderContent = repository.getAllFolderContentSync(folderId)
        val storedFolder = repository.getFolder(folderId)
        val folder = if (storedFolder != null) {
            val childrenIds = mutableListOf<String>()
            for (bookmark in folderContent.first) {
                if (bookmark.deleted == null) {
                    childrenIds.add(bookmark.id)
                }
            }
            for (eachFolder in folderContent.second) {
                if (eachFolder.deleted == null) {
                    childrenIds.add(eachFolder.id)
                }
            }
            encryptFolder(storedFolder, childrenIds)
        } else {
            null
        }
        return folder
    }

    private fun addFolderEntryWithContent(
        folderId: String,
        updates: MutableList<SyncBookmarkEntry>,
    ): List<SyncBookmarkEntry> {
        repository.getAllFolderContentSync(folderId).apply {
            val folder = repository.getFolder(folderId)
            if (folder != null) {
                val childrenIds = mutableListOf<String>()
                for (bookmark in this.first) {
                    if (bookmark.deleted != null) {
                        updates.add(deletedEntry(bookmark.id, bookmark.deleted!!))
                    } else {
                        childrenIds.add(bookmark.id)
                        updates.add(encryptSavedSite(bookmark))
                    }
                }
                for (eachFolder in this.second) {
                    if (eachFolder.deleted != null) {
                        updates.add(deletedEntry(eachFolder.id, eachFolder.deleted!!))
                    } else {
                        childrenIds.add(eachFolder.id)
                        addFolderEntryWithContent(eachFolder.id, updates)
                    }
                }
                updates.add(encryptFolder(folder, childrenIds))
            }
        }
        return updates
    }

    private fun encryptSavedSite(
        savedSite: SavedSite,
    ): SyncBookmarkEntry {
        return SyncBookmarkEntry(
            id = savedSite.id,
            title = syncCrypto.encrypt(savedSite.title),
            page = SyncBookmarkPage(syncCrypto.encrypt(savedSite.url)),
            folder = null,
            deleted = null,
            client_last_modified = savedSite.lastModified ?: DatabaseDateFormatter.iso8601(),
        )
    }

    private fun encryptFolder(
        bookmarkFolder: BookmarkFolder,
        children: List<String>,
    ): SyncBookmarkEntry {
        return SyncBookmarkEntry(
            id = bookmarkFolder.id,
            title = syncCrypto.encrypt(bookmarkFolder.name),
            folder = SyncFolderChildren(children),
            page = null,
            deleted = bookmarkFolder.deleted,
            client_last_modified = bookmarkFolder.lastModified ?: DatabaseDateFormatter.iso8601(),
        )
    }

    private fun deletedEntry(
        id: String,
        deleted: String,
    ): SyncBookmarkEntry {
        return SyncBookmarkEntry(
            id = id,
            title = null,
            folder = null,
            page = null,
            deleted = "1",
            client_last_modified = null,
        )
    }

    private fun formatUpdates(updates: List<SyncBookmarkEntry>): SyncChangesRequest {
        val modifiedSince = if (savedSitesSyncStore.serverModifiedSince == "0") {
            ModifiedSince.FirstSync
        } else {
            ModifiedSince.Timestamp(savedSitesSyncStore.serverModifiedSince)
        }

        return if (updates.isEmpty()) {
            SyncChangesRequest(BOOKMARKS, "", modifiedSince)
        } else {
            val bookmarkUpdates = SyncBookmarkUpdates(updates, savedSitesSyncStore.serverModifiedSince)
            val patch = SyncBookmarksRequest(bookmarkUpdates, DatabaseDateFormatter.iso8601())
            val allDataJSON = Adapters.patchAdapter.toJson(patch)
            SyncChangesRequest(BOOKMARKS, allDataJSON, modifiedSince)
        }
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val patchAdapter: JsonAdapter<SyncBookmarksRequest> =
                moshi.adapter(SyncBookmarksRequest::class.java)
        }
    }
}

data class SyncBookmarkPage(val url: String)
data class SyncFolderChildren(val children: List<String>)

data class SyncBookmarkEntry(
    val id: String,
    val title: String?,
    val page: SyncBookmarkPage?,
    val folder: SyncFolderChildren?,
    val deleted: String?,
    val client_last_modified: String?,
)

class SyncBookmarksRequest(
    val bookmarks: SyncBookmarkUpdates,
    val client_timestamp: String,
)

class SyncBookmarkUpdates(
    val updates: List<SyncBookmarkEntry>,
    val modified_since: String = "0",
)

fun SyncBookmarkEntry.isFolder(): Boolean = this.folder != null
fun SyncBookmarkEntry.titleOrFallback(): String = this.title ?: "Bookmark"

fun SyncBookmarkEntry.isBookmarksRoot(): Boolean = this.folder != null && this.id == SavedSitesNames.BOOKMARKS_ROOT
fun SyncBookmarkEntry.isFavouritesRoot(): Boolean = this.folder != null && this.id == SavedSitesNames.FAVORITES_ROOT
fun SyncBookmarkEntry.isBookmark(): Boolean = this.page != null
