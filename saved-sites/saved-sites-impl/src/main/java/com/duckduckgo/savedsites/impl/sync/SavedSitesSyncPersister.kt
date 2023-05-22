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
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.sync.SavedSitesDuplicateResult.Duplicate
import com.duckduckgo.savedsites.impl.sync.SavedSitesDuplicateResult.NotDuplicate
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.FeatureSyncStore
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncDataValidationResult
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import com.duckduckgo.sync.api.engine.SyncableDataPersister
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.DEDUPLICATION
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.REMOTE_WINS
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.TIMESTAMP
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject
import org.threeten.bp.OffsetDateTime
import timber.log.Timber

@ContributesMultibinding(scope = AppScope::class, boundType = SyncableDataPersister::class)
class SavedSitesSyncPersister @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val savedSitesSyncStore: FeatureSyncStore,
    private val duplicateFinder: SavedSitesDuplicateFinder,
    private val syncCrypto: SyncCrypto,
) : SyncableDataPersister {

    override fun persist(
        changes: List<SyncChangesResponse>,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult<Boolean> {
        changes.find { it.type == BOOKMARKS }?.let { bookmarkChanges ->
            Timber.d("Sync-Feature: received remote changes, merging with resolution $conflictResolution")
            val result = merge(bookmarkChanges, conflictResolution)
            Timber.d("Sync-Feature: merging bookmarks finished with $result")
            return SyncMergeResult.Success(true)
        }
        Timber.d("Sync-Feature: no bookmarks to merge")
        return SyncMergeResult.Success(false)
    }

    override fun onSyncDisabled() {
        savedSitesSyncStore.modifiedSince = "0"
    }

    fun merge(
        changes: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult<Boolean> {
        val result = when (val validation = validateChanges(changes)) {
            is SyncDataValidationResult.Error -> SyncMergeResult.Error(reason = validation.reason)
            is SyncDataValidationResult.Success -> processEntries(validation.data, conflictResolution)
            else -> SyncMergeResult.Error(reason = "Something went wrong")
        }

        if (result is Success) {
            pruneDeletedObjects()
        }

        return result
    }

    private fun validateChanges(changes: SyncChangesResponse): SyncDataValidationResult<SyncBookmarkEntries> {
        val response = kotlin.runCatching { Adapters.updatesAdapter.fromJson(changes.jsonString) }.getOrNull()

        if (response == null) {
            return SyncDataValidationResult.Error(reason = "Sync-Feature: merging failed, JSON format incorrect bookmarks null")
        }

        if (response.bookmarks.entries == null) {
            Timber.d("Sync-Feature: JSON has null entries, replacing with empty list")
            return SyncDataValidationResult.Success(SyncBookmarkEntries(emptyList(), response.bookmarks.last_modified))
        }

        return SyncDataValidationResult.Success(response.bookmarks)
    }

    private fun processEntries(
        bookmarks: SyncBookmarkEntries,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult<Boolean> {
        Timber.d("Sync-Feature: updating bookmarks last_modified to ${bookmarks.last_modified}")
        savedSitesSyncStore.modifiedSince = bookmarks.last_modified

        if (bookmarks.entries.isEmpty()) {
            Timber.d("Sync-Feature: merging completed, no entries to merge")
            return SyncMergeResult.Success(false)
        }

        val processIds: MutableList<String> = mutableListOf()
        val allResponseIds = bookmarks.entries.map { it.id }
        val allFolders = bookmarks.entries.filter { it.isFolder() }
        val allFolderIds = allFolders.map { it.id }
        val allChildren = mutableListOf<String>()
        allFolders.forEach { entry ->
            entry.folder?.children?.forEach {
                if (!allChildren.contains(it)) {
                    allChildren.add(it)
                }
            }
        }

        // Iterate over received items and find:
        // 1. All folders without a parent in the payload
        // check all children, the ones that are not in allFolders don't have a parent
        val foldersWithoutParent = allFolderIds.filterNot { allChildren.contains(it) }
        foldersWithoutParent.forEach { folderId ->
            processFolder(folderId, "", bookmarks.entries, bookmarks.last_modified, processIds, conflictResolution)
        }

        // 2. All bookmarks without a parent in the payload
        val allBookmarkIds = bookmarks.entries.filter { it.isBookmark() }.map { it.id }
        val bookmarksWithoutParent = allBookmarkIds.filterNot { allChildren.contains(it) }
        bookmarksWithoutParent.forEach { bookmarkId ->
            processBookmark(bookmarkId, processIds, bookmarks.entries, SavedSitesNames.BOOKMARKS_ROOT, bookmarks.last_modified, conflictResolution)
        }

        // Favourites
        if (allResponseIds.contains(SavedSitesNames.FAVORITES_ROOT)) {
            Timber.d("Sync-Feature: favourites root found, traversing from there")
            processFavourites(bookmarks.entries, bookmarks.last_modified, conflictResolution)
            processIds.add(SavedSitesNames.FAVORITES_ROOT)
        }

        val unprocessedIds = allResponseIds.filterNot { processIds.contains(it) }
        Timber.d("Sync-Feature: there are ${unprocessedIds.size} items orphaned $unprocessedIds")

        return SyncMergeResult.Success(true)
    }

    private fun processFolder(
        folderId: String,
        parentId: String,
        remoteUpdates: List<SyncBookmarkEntry>,
        lastModified: String,
        processIds: MutableList<String>,
        conflictResolution: SyncConflictResolution,
    ) {
        val remoteFolder = remoteUpdates.find { it.id == folderId }
        if (remoteFolder == null) {
            Timber.d("Sync-Feature: merging folder $folderId with parentId $parentId")
            Timber.d("Sync-Feature: can't find folder $folderId")
        } else {
            processRemoteFolder(remoteFolder, parentId, lastModified, processIds, conflictResolution, folderId)
            remoteFolder.folder?.children?.forEach { child ->
                processBookmark(child, processIds, remoteUpdates, folderId, lastModified, conflictResolution)
            }
        }
    }

    private fun processRemoteFolder(
        remoteFolder: SyncBookmarkEntry,
        parentId: String,
        lastModified: String,
        processIds: MutableList<String>,
        conflictResolution: SyncConflictResolution,
        folderId: String,
    ) {
        remoteFolder.folder?.let {
            val folder = decryptFolder(remoteFolder, parentId, lastModified)
            processIds.add(folder.id)
            if (folder.id != SavedSitesNames.BOOKMARKS_ROOT && folder.id != SavedSitesNames.FAVORITES_ROOT) {
                Timber.d("Sync-Feature: merging folder $folderId with parentId $parentId")
                if (conflictResolution == DEDUPLICATION) {
                    // in deduplication we replace local folder with remote folder (id, name, parentId, add children to existent ones)
                    when (val result = duplicateFinder.findFolderDuplicate(folder)) {
                        is Duplicate -> {
                            if (folder.deleted != null) {
                                Timber.d("Sync-Feature: folder $folderId has a local duplicate in ${result.id} and needs to be deleted")
                                savedSitesRepository.delete(folder)
                            } else {
                                Timber.d("Sync-Feature: folder $folderId has a local duplicate in ${result.id}, replacing content")
                                savedSitesRepository.replaceFolderContent(folder, result.id)
                            }
                        }

                        is NotDuplicate -> {
                            if (folder.deleted != null) {
                                Timber.d("Sync-Feature: folder $folderId not present locally but was deleted, nothing to do")
                            } else {
                                Timber.d("Sync-Feature: folder $folderId not present locally, inserting")
                                savedSitesRepository.insert(folder)
                            }
                        }
                    }
                } else {
                    // if there's a folder with the same id locally we check the conflict resolution
                    // if TIMESTAMP -> new timestamp wins
                    // if REMOTE -> remote object wins and replaces local
                    // if LOCAL -> local object wins and no changes are applied
                    val localFolder = savedSitesRepository.getFolder(folder.id)
                    if (localFolder != null) {
                        when (conflictResolution) {
                            REMOTE_WINS -> {
                                if (folder.deleted != null) {
                                    Timber.d("Sync-Feature: folder $folderId exists locally but was deleted remotely, deleting locally too")
                                    savedSitesRepository.delete(localFolder)
                                } else {
                                    Timber.d("Sync-Feature: folder $folderId exists locally, replacing content")
                                    savedSitesRepository.replaceFolderContent(folder, folder.id)
                                }
                            }

                            TIMESTAMP -> {
                                if (folder.modifiedAfter(localFolder.lastModified)) {
                                    if (folder.deleted != null) {
                                        Timber.d("Sync-Feature: folder $folderId deleted after local folder")
                                        savedSitesRepository.delete(localFolder)
                                    } else {
                                        Timber.d("Sync-Feature: folder $folderId modified after local folder, replacing content")
                                        savedSitesRepository.replaceFolderContent(folder, folder.id)
                                    }
                                } else {
                                    Timber.d("Sync-Feature: folder $folderId modified before local folder, nothing to do")
                                }
                            }

                            else -> {
                                Timber.d("Sync-Feature: local folder wins over remote, nothing to do")
                            }
                        }
                    } else {
                        if (folder.deleted != null) {
                            Timber.d("Sync-Feature: folder $folderId not present locallybut was deleted, nothing to do")
                        } else {
                            Timber.d("Sync-Feature: folder $folderId not present locally, inserting")
                            savedSitesRepository.insert(folder)
                        }
                    }
                }
            }
        }
    }

    private fun processBookmark(
        child: String,
        processIds: MutableList<String>,
        remoteUpdates: List<SyncBookmarkEntry>,
        folderId: String,
        lastModified: String,
        conflictResolution: SyncConflictResolution,
    ) {
        Timber.d("Sync-Feature: merging child $child")
        processIds.add(child)
        val childEntry = remoteUpdates.find { it.id == child }
        if (childEntry == null) {
            Timber.d("Sync-Feature: can't find child $child")
        } else {
            when {
                childEntry.isBookmark() -> {
                    Timber.d("Sync-Feature: child $child is a Bookmark")
                    val bookmark = decryptBookmark(childEntry, folderId, lastModified)
                    if (conflictResolution == DEDUPLICATION) {
                        // if there's a bookmark duplicate locally (url and name) then we replace it
                        when (val result = duplicateFinder.findBookmarkDuplicate(bookmark)) {
                            is Duplicate -> {
                                Timber.d("Sync-Feature: child $child has a local duplicate in ${result.id}, replacing")
                                savedSitesRepository.replaceBookmark(bookmark, result.id)
                            }

                            is NotDuplicate -> {
                                Timber.d("Sync-Feature: child $child not present locally, inserting")
                                savedSitesRepository.insert(bookmark)
                            }
                        }
                    } else {
                        // if there's a bookmark with the same id locally we check the conflict resolution
                        // if TIMESTAMP -> new timestamp wins
                        // if REMOTE -> remote object wins and replaces local
                        // if LOCAL -> local object wins and no changes are applied
                        val storedBookmark = savedSitesRepository.getBookmarkById(child)
                        if (storedBookmark != null) {
                            when (conflictResolution) {
                                REMOTE_WINS -> {
                                    Timber.d("Sync-Feature: child $child exists locally, replacing")
                                    savedSitesRepository.replaceBookmark(bookmark, child)
                                }

                                TIMESTAMP -> {
                                    if (bookmark.modifiedAfter(storedBookmark.lastModified)) {
                                        Timber.d("Sync-Feature: bookmark ${bookmark.id} modified after local bookmark, replacing content")
                                        savedSitesRepository.replaceBookmark(bookmark, child)
                                    } else {
                                        Timber.d("Sync-Feature: bookmark ${bookmark.id} modified before local bookmark, nothing to do")
                                    }
                                }

                                else -> {
                                    Timber.d("Sync-Feature: local bookmark wins over remote, nothing to do")
                                }
                            }
                        } else {
                            Timber.d("Sync-Feature: child $child not present locally, inserting")
                            savedSitesRepository.insert(bookmark)
                        }
                    }
                }

                childEntry.isFolder() -> {
                    Timber.d("Sync-Feature: child $child is a Folder")
                    processFolder(childEntry.id, folderId, remoteUpdates, lastModified, processIds, conflictResolution)
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
            name = syncCrypto.decrypt(remoteEntry.titleOrFallback()),
            parentId = parentId,
            lastModified = remoteEntry.client_last_modified ?: lastModified,
            deleted = remoteEntry.deleted,
        )
        Timber.d("Sync-Feature: decrypted $folder")
        return folder
    }

    /**
     * If we need to deduplicate (account login) then we want to replace favourite if exist
     * otherwise we add them to the favourites folder
     */
    private fun processFavourites(
        entries: List<SyncBookmarkEntry>,
        lastModified: String,
        conflictResolution: SyncConflictResolution,
    ) {
        val favourites = entries.find { it.id == SavedSitesNames.FAVORITES_ROOT }!!
        favourites.folder?.children?.forEachIndexed { position, child ->
            Timber.d("Sync-Feature: child $child is a Favourite")
            val favouriteEntry = entries.find { it.id == child }
            if (favouriteEntry == null) {
                Timber.d("Sync-Feature: can't find favourite $child")
            } else {
                val favourite = decryptFavourite(favouriteEntry, position, lastModified)
                if (conflictResolution == DEDUPLICATION) {
                    Timber.d("Sync-Feature: is $child duplicated locally?")
                    when (val result = duplicateFinder.findFavouriteDuplicate(favourite)) {
                        is SavedSitesDuplicateResult.Duplicate -> {
                            Timber.d("Sync-Feature: child $child exists locally, replacing")
                            savedSitesRepository.replaceFavourite(favourite, result.id)
                        }

                        is SavedSitesDuplicateResult.NotDuplicate -> {
                            Timber.d("Sync-Feature: child $child not present locally, inserting")
                            savedSitesRepository.insert(favourite)
                        }
                    }
                } else {
                    Timber.d("Sync-Feature: adding $child to Favourites")
                    savedSitesRepository.insert(favourite)
                }
            }
        }
    }

    private fun pruneDeletedObjects() {
        savedSitesRepository.pruneDeleted()
    }

    private fun decryptBookmark(
        remoteEntry: SyncBookmarkEntry,
        parentId: String,
        lastModified: String,
    ): Bookmark {
        val bookmark = Bookmark(
            id = remoteEntry.id,
            title = syncCrypto.decrypt(remoteEntry.titleOrFallback()),
            url = syncCrypto.decrypt(remoteEntry.page!!.url),
            parentId = parentId,
            lastModified = remoteEntry.client_last_modified ?: lastModified,
            deleted = remoteEntry.deleted,
        )
        Timber.d("Sync-Feature: decrypted $bookmark")
        return bookmark
    }

    private fun decryptFavourite(
        remoteEntry: SyncBookmarkEntry,
        position: Int,
        lastModified: String,
    ): Favorite {
        val favourite = Favorite(
            id = remoteEntry.id,
            title = syncCrypto.decrypt(remoteEntry.titleOrFallback()),
            url = syncCrypto.decrypt(remoteEntry.page!!.url),
            lastModified = remoteEntry.client_last_modified ?: lastModified,
            position = position,
        )
        Timber.d("Sync-Feature: decrypted $favourite")
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

data class SyncBookmarkRemoteUpdates(
    val bookmarks: SyncBookmarkEntries,
    val client_timestamp: String,
)

data class SyncBookmarkEntries(
    val entries: List<SyncBookmarkEntry>,
    val last_modified: String,
)

fun BookmarkFolder.modifiedAfter(after: String?): Boolean {
    return if (this.lastModified == null) {
        true
    } else {
        if (after == null) {
            false
        } else {
            val entityModified = OffsetDateTime.parse(this.lastModified)
            val sinceModified = OffsetDateTime.parse(after)
            entityModified.isAfter(sinceModified)
        }
    }
}

fun SavedSite.modifiedAfter(after: String?): Boolean {
    return if (this.lastModified == null) {
        true
    } else {
        if (after == null) {
            false
        } else {
            val entityModified = OffsetDateTime.parse(this.lastModified)
            val sinceModified = OffsetDateTime.parse(after)
            entityModified.isAfter(sinceModified)
        }
    }
}

// eyJyZWNvdmVyeSI6eyJwcmltYXJ5X2tleSI6IkU0UkNPRkdtakZPRCtUT1RtRkMveTNPaEJwNStDaFRZcG9OTEdRSno0Rlk9IiwidXNlcl9pZCI6IjQwZTFjMzcwLTYxZWUtNDYxMy1iMTI1LWRjOGQ5MjljMmYxZSJ9fQ==
