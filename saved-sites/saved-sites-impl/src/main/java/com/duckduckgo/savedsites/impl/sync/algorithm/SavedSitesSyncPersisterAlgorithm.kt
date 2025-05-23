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

package com.duckduckgo.savedsites.impl.sync.algorithm

import com.duckduckgo.di.scopes.*
import com.duckduckgo.savedsites.api.*
import com.duckduckgo.savedsites.api.models.*
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames.BOOKMARKS_ROOT
import com.duckduckgo.savedsites.api.models.SavedSitesNames.FAVORITES_DESKTOP_ROOT
import com.duckduckgo.savedsites.api.models.SavedSitesNames.FAVORITES_MOBILE_ROOT
import com.duckduckgo.savedsites.api.models.SavedSitesNames.FAVORITES_ROOT
import com.duckduckgo.savedsites.impl.sync.*
import com.duckduckgo.sync.api.*
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.*
import com.squareup.anvil.annotations.*
import javax.inject.*
import logcat.LogPriority.INFO
import logcat.logcat

interface SavedSitesSyncPersisterAlgorithm {
    fun processEntries(
        bookmarks: SyncBookmarkEntries,
        conflictResolution: SyncConflictResolution,
        clientModifiedSince: String,
    ): SyncMergeResult
}

@ContributesBinding(AppScope::class)
class RealSavedSitesSyncPersisterAlgorithm @Inject constructor(
    private val syncCrypto: SyncCrypto,
    private val savedSitesRepository: SavedSitesRepository,
    private val syncSavedSitesRepository: SyncSavedSitesRepository,
    @Named("deduplicationStrategy") private val deduplicationStrategy: SavedSitesSyncPersisterStrategy,
    @Named("timestampStrategy") private val timestampStrategy: SavedSitesSyncPersisterStrategy,
    @Named("remoteWinsStrategy") private val remoteWinsStrategy: SavedSitesSyncPersisterStrategy,
    @Named("localWinsStrategy") private val localWinsStrategy: SavedSitesSyncPersisterStrategy,
) : SavedSitesSyncPersisterAlgorithm {
    override fun processEntries(
        bookmarks: SyncBookmarkEntries,
        conflictResolution: SyncConflictResolution,
        clientModifiedSince: String,
    ): SyncMergeResult {
        var orphans = false
        var timestampConflict = false

        val processIds: MutableList<String> = mutableListOf(SavedSitesNames.BOOKMARKS_ROOT)
        val allResponseIds = bookmarks.entries.filterNot { it.deleted != null }.map { it.id }
        val allFolders = bookmarks.entries.filter { it.isFolder() }
            .filterNot {
                it.id == FAVORITES_ROOT || it.id == FAVORITES_MOBILE_ROOT || it.id == FAVORITES_DESKTOP_ROOT
            }
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
            val folder = savedSitesRepository.getFolder(folderId)
            if (folder != null) {
                processIds.add(folderId)
            }
            if (folder != null && folder.modifiedAfter(clientModifiedSince) && conflictResolution != DEDUPLICATION) {
                logcat { "Sync-Bookmarks: Timestamp conflict found for folder $folderId" }
                timestampConflict = true
            } else {
                processFolder(folderId, SavedSitesNames.BOOKMARKS_ROOT, bookmarks.entries, clientModifiedSince, processIds, conflictResolution)
            }
        }

        // 2. All bookmarks without a parent in the payload
        val allBookmarkIds = bookmarks.entries.filter { it.isBookmark() }.map { it.id }
        val bookmarksWithoutParent = allBookmarkIds.filterNot { allChildren.contains(it) }
        bookmarksWithoutParent.forEach { bookmarkId ->
            val savedSite = savedSitesRepository.getSavedSite(bookmarkId)
            if (savedSite != null) {
                processIds.add(bookmarkId)
            }
            if (savedSite != null && savedSite.modifiedAfter(clientModifiedSince) && conflictResolution != DEDUPLICATION) {
                logcat { "Sync-Bookmarks: Timestamp conflict found for bookmark $bookmarkId, skipping entry" }
                timestampConflict = true
            } else {
                processChild(
                    conflictResolution,
                    bookmarkId,
                    processIds,
                    bookmarks.entries,
                    SavedSitesNames.BOOKMARKS_ROOT,
                    clientModifiedSince,
                )
            }
        }

        // 3. All objects deleted in the root
        val allDeletedIds = bookmarks.entries.filter { it.deleted != null }.map { it.id }
        processDeletedItems(allDeletedIds)

        // Favourites
        val favoriteFolders = listOf(FAVORITES_ROOT, FAVORITES_MOBILE_ROOT, FAVORITES_DESKTOP_ROOT)
        favoriteFolders.forEach { favoriteFolder ->
            if (allResponseIds.contains(favoriteFolder)) {
                processFavouritesFolder(
                    conflictResolution = conflictResolution,
                    entries = bookmarks.entries,
                    favoriteFolder = favoriteFolder,
                )
                processIds.add(favoriteFolder)
            }
        }

        // Bookmarks Root
        if (allResponseIds.contains(BOOKMARKS_ROOT)) {
            processBookmarksRootFolder(bookmarks.entries)
        }

        // there are two types of orphans
        // 1 - they come from the response -> (entity in response doesn't belong to any folder)
        // 2 - they are created because the response (local entity is no longer part of a folder)
        // for the first ones we already process them above (adding them to bookmarks root)
        // the second ones are attached to bookmarks root here, since we can't do it earlier
        val unprocessedIds = allResponseIds.filterNot { processIds.contains(it) }
        if (unprocessedIds.isNotEmpty()) {
            orphans = true
            logcat { "Sync-Bookmarks: there are ${unprocessedIds.size} items orphan in the response $unprocessedIds" }
        }

        val fixedOrphans = syncSavedSitesRepository.fixOrphans()
        if (fixedOrphans) {
            logcat { "Sync-Bookmarks: fixed orphans, attached them to root" }
            orphans = true
        }

        return SyncMergeResult.Success(orphans = orphans, timestampConflict = timestampConflict)
    }

    private fun processFolder(
        folderId: String,
        parentId: String,
        remoteEntries: List<SyncSavedSitesResponseEntry>,
        lastModified: String,
        processIds: MutableList<String>,
        conflictResolution: SyncConflictResolution,
    ) {
        val remoteFolder = remoteEntries.find { it.id == folderId }
        if (remoteFolder == null) {
            logcat { "Sync-Bookmarks: processing folder $folderId with parentId $parentId" }
            logcat { "Sync-Bookmarks: can't find folder $folderId" }
        } else {
            processBookmarkFolder(conflictResolution, remoteFolder, parentId, lastModified)
            remoteFolder.folder?.children?.forEach { child ->
                processIds.add(child)
                processChild(conflictResolution, child, processIds, remoteEntries, folderId, lastModified)
            }
        }
    }

    private fun processBookmarkFolder(
        conflictResolution: SyncConflictResolution,
        remoteFolder: SyncSavedSitesResponseEntry,
        parentId: String,
        lastModified: String,
    ) {
        val folder = decryptFolder(remoteFolder, parentId, lastModified)
        if (folder.id != SavedSitesNames.BOOKMARKS_ROOT && folder.id != FAVORITES_ROOT) {
            logcat { "Sync-Bookmarks: processing folder ${folder.id} with parentId $parentId" }
            when (conflictResolution) {
                DEDUPLICATION -> deduplicationStrategy.processBookmarkFolder(folder, remoteFolder.folder?.children ?: emptyList())
                REMOTE_WINS -> remoteWinsStrategy.processBookmarkFolder(folder, remoteFolder.folder?.children ?: emptyList())
                LOCAL_WINS -> localWinsStrategy.processBookmarkFolder(folder, remoteFolder.folder?.children ?: emptyList())
                TIMESTAMP -> timestampStrategy.processBookmarkFolder(folder, remoteFolder.folder?.children ?: emptyList())
            }
        }
    }

    private fun processChild(
        conflictResolution: SyncConflictResolution,
        child: String,
        processIds: MutableList<String>,
        entries: List<SyncSavedSitesResponseEntry>,
        folderId: String,
        lastModified: String,
    ) {
        logcat { "Sync-Bookmarks: processing id $child" }
        val childEntry = entries.find { it.id == child }
        if (childEntry == null) {
            logcat { "Sync-Bookmarks: id $child not present in the payload, omitting" }
        } else {
            when {
                childEntry.isBookmark() -> {
                    processBookmark(childEntry, conflictResolution, folderId, lastModified)
                }

                childEntry.isFolder() -> {
                    logcat { "Sync-Bookmarks: child $child is a Folder" }
                    processFolder(childEntry.id, folderId, entries, lastModified, processIds, conflictResolution)
                }
            }
        }
    }

    private fun processBookmark(
        childEntry: SyncSavedSitesResponseEntry,
        conflictResolution: SyncConflictResolution,
        folderId: String,
        lastModified: String,
    ) {
        logcat { "Sync-Bookmarks: child ${childEntry.id} is a Bookmark" }
        val bookmark = decryptBookmark(childEntry, folderId, lastModified)
        when (conflictResolution) {
            DEDUPLICATION -> deduplicationStrategy.processBookmark(bookmark, folderId)
            REMOTE_WINS -> remoteWinsStrategy.processBookmark(bookmark, folderId)
            LOCAL_WINS -> localWinsStrategy.processBookmark(bookmark, folderId)
            TIMESTAMP -> timestampStrategy.processBookmark(bookmark, folderId)
        }
    }

    private fun processFavouritesFolder(
        conflictResolution: SyncConflictResolution,
        entries: List<SyncSavedSitesResponseEntry>,
        favoriteFolder: String,
    ) {
        logcat(INFO) { "Sync-Bookmarks: processing favourites folder $favoriteFolder" }
        val favouriteFolder = entries.find { it.id == favoriteFolder } ?: return
        val favourites = favouriteFolder.folder?.children ?: emptyList()

        when (conflictResolution) {
            DEDUPLICATION -> deduplicationStrategy.processFavouritesFolder(favoriteFolder, favourites)
            REMOTE_WINS -> remoteWinsStrategy.processFavouritesFolder(favoriteFolder, favourites)
            LOCAL_WINS -> localWinsStrategy.processFavouritesFolder(favoriteFolder, favourites)
            TIMESTAMP -> timestampStrategy.processFavouritesFolder(favoriteFolder, favourites)
        }
    }

    private fun processBookmarksRootFolder(entries: List<SyncSavedSitesResponseEntry>) {
        logcat(INFO) { "Sync-Bookmarks: processing bookmarks root folder" }
        val rootEntry = entries.find { it.id == BOOKMARKS_ROOT } ?: return
        val rootContent = rootEntry.folder?.children ?: emptyList()
        if (rootContent.isNotEmpty()) {
            val rootFolder = savedSitesRepository.getFolder(BOOKMARKS_ROOT)
            if (rootFolder != null) {
                syncSavedSitesRepository.replaceBookmarkFolder(rootFolder, rootContent)
            }
        }
    }

    private fun processDeletedItems(deletedItems: List<String>) {
        logcat { "Sync-Bookmarks: processing deleted items $deletedItems" }
        deletedItems.forEach { id ->
            val isBookmark = savedSitesRepository.getBookmarkById(id)
            if (isBookmark != null) {
                logcat { "Sync-Bookmarks: item $id is a bookmark, deleting it" }
                savedSitesRepository.delete(isBookmark)
            }
            val isFavourite = savedSitesRepository.getFavoriteById(id)
            if (isFavourite != null) {
                logcat { "Sync-Bookmarks: item $id is a favourite, deleting it" }
                savedSitesRepository.delete(isFavourite)
            }
            val isFolder = savedSitesRepository.getFolder(id)
            if (isFolder != null) {
                logcat { "Sync-Bookmarks: item $id is a folder, deleting it" }
                savedSitesRepository.delete(isFolder)
            }
        }
    }

    private fun decryptFolder(
        remoteEntry: SyncSavedSitesResponseEntry,
        parentId: String,
        lastModified: String,
    ): BookmarkFolder {
        val folder = BookmarkFolder(
            id = remoteEntry.id,
            name = syncCrypto.decrypt(remoteEntry.titleOrFallback()),
            parentId = parentId,
            lastModified = remoteEntry.last_modified ?: lastModified,
            deleted = remoteEntry.deleted,
        )
        logcat { "Sync-Bookmarks: decrypted $folder" }
        return folder
    }

    private fun decryptBookmark(
        remoteEntry: SyncSavedSitesResponseEntry,
        parentId: String,
        lastModified: String,
    ): Bookmark {
        val bookmark = Bookmark(
            id = remoteEntry.id,
            title = syncCrypto.decrypt(remoteEntry.titleOrFallback()),
            url = syncCrypto.decrypt(remoteEntry.page!!.url),
            parentId = parentId,
            lastModified = remoteEntry.last_modified ?: lastModified,
            deleted = remoteEntry.deleted,
        )
        logcat { "Sync-Bookmarks: decrypted $bookmark" }
        return bookmark
    }

    private fun decryptFavourite(
        remoteEntry: SyncSavedSitesResponseEntry,
        position: Int,
        lastModified: String,
    ): Favorite {
        val favourite = Favorite(
            id = remoteEntry.id,
            title = syncCrypto.decrypt(remoteEntry.titleOrFallback()),
            url = syncCrypto.decrypt(remoteEntry.page!!.url),
            lastModified = remoteEntry.last_modified ?: lastModified,
            position = position,
        )
        logcat { "Sync-Bookmarks: decrypted $favourite" }
        return favourite
    }
}
