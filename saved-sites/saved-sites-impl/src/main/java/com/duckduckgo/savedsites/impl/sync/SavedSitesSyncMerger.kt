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

import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.FeatureSyncStore
import com.duckduckgo.sync.api.engine.SyncChanges
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMerger
import com.duckduckgo.sync.api.engine.SyncablePlugin
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(scope = AppScope::class, boundType = SyncablePlugin::class)
@ContributesBinding(scope = AppScope::class, boundType = SyncMerger::class)
class SavedSitesSyncMerger @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val savedSitesSyncStore: FeatureSyncStore,
    private val syncCrypto: SyncCrypto,
) : SyncMerger, SyncablePlugin {

    override fun merge(changes: SyncChanges): SyncMergeResult<Boolean> {
        Timber.d("Sync: merging remote bookmarks changes $changes")
        val remoteUpdates = Adapters.updatesAdapter.fromJson(changes.updatesJSON)
        if (remoteUpdates == null) {
            return SyncMergeResult.Error(reason = "Sync: merging failed, JSON format incorrect remoteUpdates null")
        }

        if (remoteUpdates.bookmarks == null) {
            return SyncMergeResult.Error(reason = "Sync: merging failed, JSON format incorrect remoteUpdates.bookmarks null")
        }

        if (remoteUpdates.bookmarks.entries.isEmpty()) {
            return SyncMergeResult.Error(reason = "Sync: merging failed, JSON format incorrect remoteUpdates.bookmarks.entries empty")
        }

        remoteUpdates.bookmarks.entries.find { it.id == SavedSitesNames.BOOMARKS_ROOT }
            ?: return SyncMergeResult.Error(reason = "Sync: merging failed, Bookmarks Root folder does not exist")

        savedSitesSyncStore.modifiedSince = remoteUpdates.bookmarks.last_modified

        mergeFolder(SavedSitesNames.BOOMARKS_ROOT, "", remoteUpdates.bookmarks.entries)
        mergeFolder(SavedSitesNames.FAVORITES_ROOT, "", remoteUpdates.bookmarks.entries)
        return SyncMergeResult.Success(true)
    }

    private fun mergeFolder(
        folderId: String,
        parentId: String,
        remoteUpdates: List<SyncBookmarkEntry>
    ) {
        val remoteFolder = remoteUpdates.find { it.id == folderId }
        remoteFolder?.let {
            savedSitesRepository.insert(decryptFolder(remoteFolder, parentId))
            it.folder?.children?.forEachIndexed { position, child ->
                val childEntry = remoteUpdates.find { it.id == child }
                if (childEntry != null) {
                    when {
                        childEntry.isBookmark() -> {
                            if (remoteFolder.isFavouritesRoot()) {
                                savedSitesRepository.insert(decryptFavourite(childEntry, position))
                            } else {
                                savedSitesRepository.insert(decryptBookmark(childEntry, folderId))
                            }
                        }

                        childEntry.isFolder() -> {
                            mergeFolder(childEntry.id, folderId, remoteUpdates)
                        }
                    }
                }
            }
        }
    }

    private fun decryptFolder(
        remoteEntry: SyncBookmarkEntry,
        parentId: String
    ): BookmarkFolder {
        val folder = BookmarkFolder(
            id = remoteEntry.id,
            name = syncCrypto.decrypt(remoteEntry.title),
            parentId = parentId,
            lastModified = remoteEntry.client_last_modified,
        )
        Timber.d("Sync: decrypted Folder $folder")
        return folder
    }

    private fun decryptBookmark(
        remoteEntry: SyncBookmarkEntry,
        parentId: String,
    ): Bookmark {
        val bookmark = Bookmark(
            id = remoteEntry.id,
            title = syncCrypto.decrypt(remoteEntry.title),
            url = syncCrypto.decrypt(remoteEntry.page!!.url),
            parentId = parentId,
            lastModified = remoteEntry.client_last_modified,
        )
        Timber.d("Sync: decrypted Bookmark $bookmark")
        return bookmark
    }

    private fun decryptFavourite(
        remoteEntry: SyncBookmarkEntry,
        position: Int
    ): Favorite {
        val favourite = Favorite(
            id = remoteEntry.id,
            title = syncCrypto.decrypt(remoteEntry.title),
            url = syncCrypto.decrypt(remoteEntry.page!!.url),
            lastModified = remoteEntry.client_last_modified,
            position = position,
        )
        Timber.d("Sync: decrypted Favourite $favourite")
        return favourite
    }

    override fun getChanges(since: String): SyncChanges {
        return SyncChanges.empty()
    }

    override fun syncChanges(
        changes: List<SyncChanges>,
        timestamp: String,
    ) {
        Timber.d("Sync: received remote changes from $timestamp")
        changes.find { it.type == BOOKMARKS }?.let { bookmarkChanges ->
            merge(bookmarkChanges)
            Timber.d("Sync: remote bookmarks changes synced")
        }
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val updatesAdapter: JsonAdapter<SyncBookmarkRemoteChanges> =
                moshi.adapter(SyncBookmarkRemoteChanges::class.java)
        }
    }
}

class SyncBookmarkRemoteChanges(
    val bookmarks: SyncBookmarkRemoteUpdates,
)

class SyncBookmarkRemoteUpdates(
    val entries: List<SyncBookmarkEntry>,
    val last_modified: String
)

