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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.sync.SyncBookmarkEntries
import com.duckduckgo.savedsites.impl.sync.SyncBookmarkEntry
import com.duckduckgo.savedsites.impl.sync.isBookmark
import com.duckduckgo.savedsites.impl.sync.isFolder
import com.duckduckgo.savedsites.impl.sync.titleOrFallback
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.DEDUPLICATION
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.LOCAL_WINS
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.REMOTE_WINS
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.TIMESTAMP
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Named
import timber.log.Timber

interface SavedSitesSyncPersisterAlgorithm {
    fun processEntries(
        bookmarks: SyncBookmarkEntries,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult
}

@ContributesBinding(AppScope::class)
class RealSavedSitesSyncPersisterAlgorithm @Inject constructor(
    private val syncCrypto: SyncCrypto,
    private val repository: SavedSitesRepository,
    @Named("deduplicationStrategy") private val deduplicationStrategy: SavedSitesSyncPersisterStrategy,
    @Named("timestampStrategy") private val timestampStrategy: SavedSitesSyncPersisterStrategy,
    @Named("remoteWinsStrategy") private val remoteWinsStrategy: SavedSitesSyncPersisterStrategy,
    @Named("localWinsStrategy") private val localWinsStrategy: SavedSitesSyncPersisterStrategy,
) : SavedSitesSyncPersisterAlgorithm {
    override fun processEntries(
        bookmarks: SyncBookmarkEntries,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult {
        var orphans = false

        val processIds: MutableList<String> = mutableListOf(SavedSitesNames.BOOKMARKS_ROOT)
        val allResponseIds = bookmarks.entries.filterNot { it.deleted != null }.map { it.id }
        val allFolders = bookmarks.entries.filter { it.isFolder() }.filterNot { it.id == SavedSitesNames.FAVORITES_ROOT }
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
            if (repository.getFolder(folderId) != null) {
                processIds.add(folderId)
            }
            processFolder(folderId, SavedSitesNames.BOOKMARKS_ROOT, bookmarks.entries, bookmarks.last_modified, processIds, conflictResolution)
        }

        // 2. All bookmarks without a parent in the payload
        val allBookmarkIds = bookmarks.entries.filter { it.isBookmark() }.map { it.id }
        val bookmarksWithoutParent = allBookmarkIds.filterNot { allChildren.contains(it) }
        bookmarksWithoutParent.forEach { bookmarkId ->
            if (repository.getSavedSite(bookmarkId) != null) {
                processIds.add(bookmarkId)
            }
            processChild(
                conflictResolution,
                bookmarkId,
                processIds,
                bookmarks.entries,
                SavedSitesNames.BOOKMARKS_ROOT,
                bookmarks.last_modified,
            )
        }

        // 3. All objects deleted in the root
        val allDeletedIds = bookmarks.entries.filter { it.deleted != null }.map { it.id }
        processDeletedItems(allDeletedIds)

        // Favourites
        if (allResponseIds.contains(SavedSitesNames.FAVORITES_ROOT)) {
            Timber.d("Sync-Bookmarks: favourites root found, traversing from there")
            processFavouritesFolder(conflictResolution, bookmarks.entries, bookmarks.last_modified)
            processIds.add(SavedSitesNames.FAVORITES_ROOT)
        }

        val unprocessedIds = allResponseIds.filterNot { processIds.contains(it) }
        if (unprocessedIds.isNotEmpty()) {
            orphans = true
            Timber.d("Sync-Bookmarks: there are ${unprocessedIds.size} items orphaned $unprocessedIds")
        }

        return SyncMergeResult.Success(orphans = orphans)
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
            Timber.d("Sync-Bookmarks: processing folder $folderId with parentId $parentId")
            Timber.d("Sync-Bookmarks: can't find folder $folderId")
        } else {
            processBookmarkFolder(conflictResolution, remoteFolder, parentId, lastModified)
            remoteFolder.folder?.children?.forEach { child ->
                processIds.add(child)
                processChild(conflictResolution, child, processIds, remoteUpdates, folderId, lastModified)
            }
        }
    }

    private fun processBookmarkFolder(
        conflictResolution: SyncConflictResolution,
        remoteFolder: SyncBookmarkEntry,
        parentId: String,
        lastModified: String,
    ) {
        val folder = decryptFolder(remoteFolder, parentId, lastModified)
        if (folder.id != SavedSitesNames.BOOKMARKS_ROOT && folder.id != SavedSitesNames.FAVORITES_ROOT) {
            Timber.d("Sync-Bookmarks: processing folder ${folder.id} with parentId $parentId")
            when (conflictResolution) {
                DEDUPLICATION -> deduplicationStrategy.processBookmarkFolder(folder)
                REMOTE_WINS -> remoteWinsStrategy.processBookmarkFolder(folder)
                LOCAL_WINS -> localWinsStrategy.processBookmarkFolder(folder)
                TIMESTAMP -> timestampStrategy.processBookmarkFolder(folder)
            }
        }
    }

    private fun processChild(
        conflictResolution: SyncConflictResolution,
        child: String,
        processIds: MutableList<String>,
        entries: List<SyncBookmarkEntry>,
        folderId: String,
        lastModified: String,
    ) {
        Timber.d("Sync-Bookmarks: processing id $child")
        val childEntry = entries.find { it.id == child }
        if (childEntry == null) {
            Timber.d("Sync-Bookmarks: id $child not present in the payload, omitting")
        } else {
            when {
                childEntry.isBookmark() -> {
                    processBookmark(childEntry, conflictResolution, folderId, lastModified)
                }

                childEntry.isFolder() -> {
                    Timber.d("Sync-Bookmarks: child $child is a Folder")
                    processFolder(childEntry.id, folderId, entries, lastModified, processIds, conflictResolution)
                }
            }
        }
    }

    private fun processBookmark(
        childEntry: SyncBookmarkEntry,
        conflictResolution: SyncConflictResolution,
        folderId: String,
        lastModified: String,
    ) {
        Timber.d("Sync-Bookmarks: child ${childEntry.id} is a Bookmark")
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
        entries: List<SyncBookmarkEntry>,
        lastModified: String,
    ) {
        val favouriteFolder = entries.find { it.id == SavedSitesNames.FAVORITES_ROOT } ?: return
        val favourites = favouriteFolder.folder?.children ?: emptyList()
        if (favourites.isEmpty()) {
            Timber.d("Sync-Bookmarks: Favourites folder is empty, removing all local favourites")
            val storedFavourites = repository.getFavoritesSync()
            storedFavourites.forEach {
                repository.delete(it)
            }
        } else {
            favourites.forEachIndexed { position, child ->
                Timber.d("Sync-Bookmarks: child $child is a Favourite")
                val favouriteEntry = entries.find { it.id == child }
                if (favouriteEntry == null) {
                    Timber.d("Sync-Bookmarks: id $child not present in the payload, has it moved position?")
                    val storedFavorite = repository.getFavoriteById(child)
                    if (storedFavorite == null) {
                        Timber.d("Sync-Bookmarks: id $child not present locally as Favourite")
                        val storedBookmark = repository.getBookmarkById(child)
                        if (storedBookmark == null) {
                            Timber.d("Sync-Bookmarks: id $child not present locally as Bookmark either, omitting")
                        } else {
                            Timber.d("Sync-Bookmarks: id $child is a Bookmark locally, adding it as Favourite")
                            repository.insertFavorite(url = storedBookmark.url, title = storedBookmark.title)
                            // repository.markBookmarkAsFavourite(id)
                        }
                    } else {
                        if (storedFavorite.position != position) {
                            Timber.d("Sync-Bookmarks: id $child present locally and moved from position ${storedFavorite.position} to $position")
                            processFavourite(conflictResolution, storedFavorite.copy(position = position, lastModified = lastModified))
                        } else {
                            Timber.d("Sync-Bookmarks: id $child present locally but in the same position")
                        }
                    }
                } else {
                    val favourite = decryptFavourite(favouriteEntry, position, lastModified)
                    processFavourite(conflictResolution, favourite)
                }
            }
        }
    }

    private fun processFavourite(
        conflictResolution: SyncConflictResolution,
        favourite: Favorite,
    ) {
        when (conflictResolution) {
            DEDUPLICATION -> deduplicationStrategy.processFavourite(favourite)
            REMOTE_WINS -> remoteWinsStrategy.processFavourite(favourite)
            LOCAL_WINS -> localWinsStrategy.processFavourite(favourite)
            TIMESTAMP -> timestampStrategy.processFavourite(favourite)
        }
    }

    private fun processDeletedItems(deletedItems: List<String>) {
        Timber.d("Sync-Bookmarks: processing deleted items $deletedItems")
        deletedItems.forEach { id ->
            val isBookmark = repository.getBookmarkById(id)
            if (isBookmark != null) {
                Timber.d("Sync-Bookmarks: item $id is a bookmark, deleting it")
                repository.delete(isBookmark)
            }
            val isFavourite = repository.getFavoriteById(id)
            if (isFavourite != null) {
                Timber.d("Sync-Bookmarks: item $id is a favourite, deleting it")
                repository.delete(isFavourite)
            }
            val isFolder = repository.getFolder(id)
            if (isFolder != null) {
                Timber.d("Sync-Bookmarks: item $id is a folder, deleting it")
                repository.delete(isFolder)
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
        Timber.d("Sync-Bookmarks: decrypted $folder")
        return folder
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
        Timber.d("Sync-Bookmarks: decrypted $bookmark")
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
        Timber.d("Sync-Bookmarks: decrypted $favourite")
        return favourite
    }
}
