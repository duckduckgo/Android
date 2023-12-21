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
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
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
    private val syncSavedSitesRepository: SyncSavedSitesRepository,
    private val savedSitesSyncStore: SavedSitesSyncStore,
    private val syncCrypto: SyncCrypto,
    private val savedSitesFormFactorSyncMigration: SavedSitesFormFactorSyncMigration,
) : SyncableDataProvider {

    override fun getChanges(): SyncChangesRequest {
        savedSitesSyncStore.startTimeStamp = DatabaseDateFormatter.iso8601()
        val updates = if (savedSitesSyncStore.serverModifiedSince == "0") {
            savedSitesFormFactorSyncMigration.onFormFactorFavouritesEnabled()
            allContent()
        } else {
            changesSince(savedSitesSyncStore.clientModifiedSince)
        }

        if (updates.isEmpty()) {
            Timber.d("Sync-Bookmarks-Metadata: no local changes, nothing to store as request")
        } else {
            syncSavedSitesRepository.addRequestMetadata(updates)
        }

        Timber.d("Sync-Bookmarks: modifiedSince changes: $updates")
        return formatUpdates(updates)
    }

    @VisibleForTesting
    fun changesSince(since: String): List<SyncSavedSitesRequestEntry> {
        Timber.i("Sync-Bookmarks: generating changes since $since")
        val updates = mutableListOf<SyncSavedSitesRequestEntry>()

        // we start adding individual folders that have been modified
        val folders = syncSavedSitesRepository.getFoldersModifiedSince(since)
        folders.forEach { folder ->
            if (folder.isDeleted()) {
                updates.add(deletedEntry(folder.id))
            } else {
                updates.add(encryptedFolder(folder))
            }
        }

        // then we add individual bookmarks that have been modified
        val bookmarks = syncSavedSitesRepository.getBookmarksModifiedSince(since)
        bookmarks.forEach { bookmark ->
            if (bookmark.isDeleted()) {
                updates.add(deletedEntry(bookmark.id))
            } else {
                updates.add(encryptedSavedSite(bookmark))
            }
        }

        return updates.distinct()
    }

    @VisibleForTesting
    fun allContent(): List<SyncSavedSitesRequestEntry> {
        Timber.i("Sync-Bookmarks: generating all content")
        val hasFavorites = repository.hasFavorites()
        val hasBookmarks = repository.hasBookmarks()

        if (!hasFavorites && !hasBookmarks) {
            Timber.d("Sync-Bookmarks: nothing to generate, favourites and bookmarks empty")
            return emptyList()
        }

        val favouriteEntries = mutableListOf<SyncSavedSitesRequestEntry>()
        // favorites (we don't add individual items, they are added as we go through bookmark folders)
        if (hasFavorites) {
            val favoritesFolders = listOf(SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_MOBILE_ROOT)
            favoritesFolders.forEach {
                val favoriteFolder = repository.getFolder(it)
                favoriteFolder?.let {
                    favouriteEntries.add(encryptedFolder(favoriteFolder))
                }
            }
        }

        return getRequestEntriesFor(SavedSitesNames.BOOKMARKS_ROOT, favouriteEntries).distinct()
    }

    private fun getRequestEntriesFor(
        folderId: String,
        requestEntries: MutableList<SyncSavedSitesRequestEntry>,
    ): List<SyncSavedSitesRequestEntry> {
        syncSavedSitesRepository.getAllFolderContentSync(folderId).apply {
            val folder = repository.getFolder(folderId)
            if (folder != null) {
                for (bookmark in this.first) {
                    if (bookmark.deleted != null) {
                        requestEntries.add(deletedEntry(bookmark.id))
                    } else {
                        requestEntries.add(encryptedSavedSite(bookmark))
                    }
                }
                for (eachFolder in this.second) {
                    if (eachFolder.deleted != null) {
                        requestEntries.add(deletedEntry(eachFolder.id))
                    } else {
                        getRequestEntriesFor(eachFolder.id, requestEntries)
                    }
                }
                requestEntries.add(encryptedFolder(folder))
            }
        }
        return requestEntries
    }

    private fun encryptedSavedSite(
        savedSite: SavedSite,
    ): SyncSavedSitesRequestEntry {
        return SyncSavedSitesRequestEntry(
            id = savedSite.id,
            title = syncCrypto.encrypt(savedSite.title),
            page = SyncBookmarkPage(syncCrypto.encrypt(savedSite.url)),
            folder = null,
            deleted = null,
            client_last_modified = savedSite.lastModified ?: DatabaseDateFormatter.iso8601(),
        )
    }

    private fun encryptedFolder(bookmarkFolder: BookmarkFolder): SyncSavedSitesRequestEntry {
        val folderChildren = syncSavedSitesRepository.getFolderDiff(bookmarkFolder.id)
        Timber.d("Sync-Bookmarks-Metadata: folder diff for ${bookmarkFolder.id} $folderChildren")
        return SyncSavedSitesRequestEntry(
            id = bookmarkFolder.id,
            title = syncCrypto.encrypt(bookmarkFolder.name),
            folder = SyncSavedSiteRequestFolder(folderChildren),
            page = null,
            deleted = bookmarkFolder.deleted,
            client_last_modified = bookmarkFolder.lastModified ?: DatabaseDateFormatter.iso8601(),
        )
    }

    private fun deletedEntry(id: String): SyncSavedSitesRequestEntry {
        return SyncSavedSitesRequestEntry(
            id = id,
            title = null,
            folder = null,
            page = null,
            deleted = "1",
            client_last_modified = null,
        )
    }

    private fun formatUpdates(updates: List<SyncSavedSitesRequestEntry>): SyncChangesRequest {
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
