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
import com.duckduckgo.sync.api.engine.SyncDataValidationResult
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMerger
import com.duckduckgo.sync.api.engine.SyncablePlugin
import com.duckduckgo.sync.api.engine.SyncablePlugin.SyncConflictResolution
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

    override fun getChanges(since: String): SyncChanges {
        return SyncChanges.empty()
    }

    override fun syncChanges(
        changes: List<SyncChanges>,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult<Boolean> {
        Timber.d("Sync: received remote changes, merging with resolution $conflictResolution")
        changes.find { it.type == BOOKMARKS }?.let { bookmarkChanges ->
            val result = merge(bookmarkChanges, conflictResolution)
            Timber.d("Sync: merging finished with $result")
            return SyncMergeResult.Success(true)
        }
        return SyncMergeResult.Success(false)
    }

    override fun merge(
        changes: SyncChanges,
        conflictResolution: SyncConflictResolution
    ): SyncMergeResult<Boolean> {
        return when (val validation = validateChanges(changes)) {
            is SyncDataValidationResult.Error -> SyncMergeResult.Error(reason = validation.reason)
            is SyncDataValidationResult.Success -> completeMerge(validation.data, conflictResolution)
            else -> SyncMergeResult.Error(reason = "Something went wrong")
        }
    }

    private fun validateChanges(changes: SyncChanges): SyncDataValidationResult<SyncBookmarkRemoteUpdates> {
        val bookmarks = kotlin.runCatching { Adapters.updatesAdapter.fromJson(changes.updatesJSON) }.getOrNull()

        if (bookmarks == null) {
            return SyncDataValidationResult.Error(reason = "Sync: merging failed, JSON format incorrect bookmarks null")
        }

        if (bookmarks.entries == null) {
            return SyncDataValidationResult.Error(reason = "Sync: merging failed, JSON format incorrect entries null")
        }

        return SyncDataValidationResult.Success(bookmarks)
    }

    private fun completeMerge(
        bookmarks: SyncBookmarkRemoteUpdates,
        conflictResolution: SyncConflictResolution
    ): SyncMergeResult<Boolean> {
        Timber.d("Sync: updating bookmarks last_modified to ${bookmarks.last_modified}")
        savedSitesSyncStore.modifiedSince = bookmarks.last_modified

        if (bookmarks.entries.isEmpty()) {
            Timber.d("Sync: merging completed, no entries to merge")
            return SyncMergeResult.Success(false)
        }

        val processIds: MutableList<String> = mutableListOf()
        val allResponseIds = bookmarks.entries.map { it.id }

        mergeFolder(SavedSitesNames.BOOMARKS_ROOT, "", bookmarks.entries, bookmarks.last_modified, processIds, conflictResolution)
        mergeFolder(SavedSitesNames.FAVORITES_ROOT, "", bookmarks.entries, bookmarks.last_modified, processIds, conflictResolution)

        val unprocessedIds = allResponseIds.filterNot { processIds.contains(it) }
        Timber.d("Sync: there are ${unprocessedIds.size} items orphaned")

        return SyncMergeResult.Success(true)
    }

    sealed class DataValidation {
        data class Error(val reason: String) : DataValidation()
        data class Success(val updates: SyncBookmarkRemoteUpdates) : DataValidation()
    }

    private fun mergeFolder(
        folderId: String,
        parentId: String,
        remoteUpdates: List<SyncBookmarkEntry>,
        lastModified: String,
        processIds: MutableList<String>,
        conflictResolution: SyncConflictResolution,
    ) {
        Timber.d("Sync: merging folder $folderId with parentId $parentId")
        val remoteFolder = remoteUpdates.find { it.id == folderId }
        if (remoteFolder == null) {
            Timber.d("Sync: can't find folder $folderId")
        } else {
            remoteFolder.folder?.let {
                val folder = decryptFolder(remoteFolder, parentId, lastModified)
                processIds.add(folder.id)
                when (val result = duplicateFinder.findFolderDuplicate(folder)) {
                    is SavedSitesDuplicateResult.Duplicate -> {
                        Timber.d("Sync: folder $folderId exists locally, replacing content")
                        savedSitesRepository.replaceFolder(folderId, result.id)
                    }

                    is SavedSitesDuplicateResult.NotDuplicate -> {
                        Timber.d("Sync: folder $folderId not present locally, inserting")
                        savedSitesRepository.insert(folder)
                    }
                }
            }

            remoteFolder.folder?.children?.forEachIndexed { position, child ->
                Timber.d("Sync: merging child $child")
                processIds.add(child)
                val childEntry = remoteUpdates.find { it.id == child }
                if (childEntry == null) {
                    Timber.d("Sync: can't find child $child")
                } else {
                    when {
                        childEntry.isBookmark() -> {
                            if (remoteFolder.isFavouritesRoot()) {
                                Timber.d("Sync: child $child is a Favourite")
                                val favourite = decryptFavourite(childEntry, position, lastModified)
                                when (val result = duplicateFinder.findFavouriteDuplicate(favourite)) {
                                    is SavedSitesDuplicateResult.Duplicate -> {
                                        Timber.d("Sync: child $child exists locally, replacing")
                                        savedSitesRepository.replaceFavourite(favourite, result.id)
                                    }

                                    is SavedSitesDuplicateResult.NotDuplicate -> {
                                        Timber.d("Sync: child $child not present locally, inserting")
                                        savedSitesRepository.insert(favourite)
                                    }
                                }
                            } else {
                                Timber.d("Sync: child $child is a Bookmark")
                                val bookmark = decryptBookmark(childEntry, folderId, lastModified)
                                when (val result = duplicateFinder.findBookmarkDuplicate(bookmark)) {
                                    is SavedSitesDuplicateResult.Duplicate -> {
                                        Timber.d("Sync: child $child exists locally, replacing")
                                        savedSitesRepository.replaceBookmark(bookmark, result.id)
                                    }

                                    is SavedSitesDuplicateResult.NotDuplicate -> {
                                        Timber.d("Sync: child $child not present locally, inserting")
                                        savedSitesRepository.insert(bookmark)
                                    }
                                }
                            }
                        }

                        childEntry.isFolder() -> {
                            Timber.d("Sync: child $child is a Folder")
                            mergeFolder(childEntry.id, folderId, remoteUpdates, lastModified, processIds, conflictResolution)
                        }
                    }
                }
            }
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
