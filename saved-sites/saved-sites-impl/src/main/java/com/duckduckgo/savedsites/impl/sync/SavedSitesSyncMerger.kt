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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
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
import javax.inject.Inject
import timber.log.Timber

@ContributesMultibinding(scope = AppScope::class, boundType = SyncablePlugin::class)
@ContributesBinding(scope = AppScope::class, boundType = SyncMerger::class)
class SavedSitesSyncMerger @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val savedSitesSyncStore: FeatureSyncStore,
    private val duplicateFinder: SavedSitesDuplicateFinder,
    private val syncCrypto: SyncCrypto,
) : SyncMerger, SyncablePlugin {

    override fun merge(changes: SyncChanges): SyncMergeResult<Boolean> {
        Timber.d("Sync: merging remote bookmarks changes $changes")

        val bookmarks = kotlin.runCatching { Adapters.updatesAdapter.fromJson(changes.updatesJSON) }.getOrNull()

        if (bookmarks == null) {
            return SyncMergeResult.Error(reason = "Sync: merging failed, JSON format incorrect bookmarks null")
        }

        if (bookmarks.last_modified != null) {
            Timber.d("Sync: updating last_modified to ${bookmarks.last_modified}")
            savedSitesSyncStore.modifiedSince = bookmarks.last_modified
        }

        if (bookmarks.entries == null) {
            return SyncMergeResult.Error(reason = "Sync: merging failed, JSON format incorrect entries null")
        }

        if (bookmarks.entries.isEmpty()) {
            Timber.d("Sync: merging completed, no entries to merge")
            return SyncMergeResult.Success(false)
        }

        bookmarks.entries.find { it.id == SavedSitesNames.BOOMARKS_ROOT }
            ?: return SyncMergeResult.Error(reason = "Sync: merging failed, Bookmarks Root folder does not exist")

        mergeFolder(SavedSitesNames.BOOMARKS_ROOT, "", bookmarks.entries, bookmarks.last_modified)
        mergeFolder(SavedSitesNames.FAVORITES_ROOT, "", bookmarks.entries, bookmarks.last_modified)
        return SyncMergeResult.Success(true)
    }

    private fun mergeFolder(
        folderId: String,
        parentId: String,
        remoteUpdates: List<SyncBookmarkEntry>,
        lastModified: String,
    ) {
        Timber.d("Sync: merging folder $folderId with parentId $parentId")
        val remoteFolder = remoteUpdates.find { it.id == folderId }
        if (remoteFolder == null) {
            Timber.d("Sync: can't find folder $folderId")
        } else {
            remoteFolder.folder?.let {
                val folder = decryptFolder(remoteFolder, parentId, lastModified)
                if (duplicateFinder.isFolderDuplicate(folder)) {
                    savedSitesRepository.replace(folderId, folder.id)
                } else {
                    savedSitesRepository.insert(folder)
                }
            }

            remoteFolder.folder?.children?.forEachIndexed { position, child ->
                Timber.d("Sync: merging child $child")
                val childEntry = remoteUpdates.find { it.id == child }
                if (childEntry == null) {
                    Timber.d("Sync: can't find child $child")
                } else {
                    when {
                        childEntry.isBookmark() -> {
                            if (remoteFolder.isFavouritesRoot()) {
                                Timber.d("Sync: child $child is a Favourite")
                                savedSitesRepository.insert(decryptFavourite(childEntry, position, lastModified))
                            } else {
                                Timber.d("Sync: child $child is a Bookmark")
                                savedSitesRepository.insert(decryptBookmark(childEntry, folderId, lastModified))
                            }
                        }

                        childEntry.isFolder() -> {
                            Timber.d("Sync: child $child is a Folder")
                            mergeFolder(childEntry.id, folderId, remoteUpdates, lastModified)
                        }
                    }
                }
            }

            savedSitesRepository.insert(decryptFolder(remoteFolder, parentId, lastModified))
        }
    }

    private fun decryptFolder(
        remoteEntry: SyncBookmarkEntry,
        parentId: String,
        lastModified: String,
    ): BookmarkFolder {
        val folder = BookmarkFolder(
            id = remoteEntry.id,
            name = syncCrypto.decrypt(remoteEntry.title),
            parentId = parentId,
            lastModified = remoteEntry.client_last_modified ?: lastModified,
        )
        Timber.d("Sync: decrypted $folder")
        return folder
    }

    private fun decryptBookmark(
        remoteEntry: SyncBookmarkEntry,
        parentId: String,
        lastModified: String,
    ): Bookmark {
        val bookmark = Bookmark(
            id = remoteEntry.id,
            title = syncCrypto.decrypt(remoteEntry.title),
            url = syncCrypto.decrypt(remoteEntry.page!!.url),
            parentId = parentId,
            lastModified = remoteEntry.client_last_modified ?: lastModified,
        )
        Timber.d("Sync: decrypted $bookmark")
        return bookmark
    }

    private fun decryptFavourite(
        remoteEntry: SyncBookmarkEntry,
        position: Int,
        lastModified: String,
    ): Favorite {
        val favourite = Favorite(
            id = remoteEntry.id,
            title = syncCrypto.decrypt(remoteEntry.title),
            url = syncCrypto.decrypt(remoteEntry.page!!.url),
            lastModified = remoteEntry.client_last_modified ?: lastModified,
            position = position,
        )
        Timber.d("Sync: decrypted $favourite")
        return favourite
    }

    override fun getChanges(since: String): SyncChanges {
        return SyncChanges.empty()
    }

    override fun syncChanges(
        changes: List<SyncChanges>,
        timestamp: String,
    ): SyncMergeResult<Boolean> {
        Timber.d("Sync: received remote changes from $timestamp")
        changes.find { it.type == BOOKMARKS }?.let { bookmarkChanges ->
            val result = merge(bookmarkChanges)
            Timber.d("Sync: merging finished with $result")
            return SyncMergeResult.Success(true)
        }
        return SyncMergeResult.Success(false)
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val updatesAdapter: JsonAdapter<SyncBookmarkRemoteUpdates> =
                moshi.adapter(SyncBookmarkRemoteUpdates::class.java)
        }
    }
}

class SyncBookmarkRemoteUpdates(
    val entries: List<SyncBookmarkEntry>,
    val last_modified: String,
)
