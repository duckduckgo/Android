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
import com.duckduckgo.sync.api.engine.SyncableDataPersister
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.DEDUPLICATION
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.LOCAL_WINS
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.REMOTE_WINS
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.TIMESTAMP
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

interface SavedSitesSyncPersisterAlgorithm {
    fun processEntries(
        bookmarks: SyncBookmarkEntries,
        conflictResolution: SyncConflictResolution
    ): SyncMergeResult<Boolean>
}

@ContributesBinding(AppScope::class)
class RealSavedSitesSyncPersisterAlgorithm @Inject constructor(
    private val syncCrypto: SyncCrypto,
    @Named("deduplicationStrategy") private val deduplicationStrategy: SavedSitesSyncPersisterStrategy,
    @Named("timestampStrategy") private val timestampStrategy: SavedSitesSyncPersisterStrategy,
    @Named("remoteWinsStrategy") private val remoteWinsStrategy: SavedSitesSyncPersisterStrategy,
    @Named("localWinsStrategy") private val localWinsStrategy: SavedSitesSyncPersisterStrategy,
) : SavedSitesSyncPersisterAlgorithm {
    override fun processEntries(
        bookmarks: SyncBookmarkEntries,
        conflictResolution: SyncConflictResolution
    ): SyncMergeResult<Boolean> {

        val processIds: MutableList<String> = mutableListOf()
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
            processFolder(folderId, "", bookmarks.entries, bookmarks.last_modified, processIds, conflictResolution)
        }

        // 2. All bookmarks without a parent in the payload
        val allBookmarkIds = bookmarks.entries.filter { it.isBookmark() }.map { it.id }
        val bookmarksWithoutParent = allBookmarkIds.filterNot { allChildren.contains(it) }
        bookmarksWithoutParent.forEach { bookmarkId ->
            processBookmark(
                conflictResolution,
                bookmarkId,
                processIds,
                bookmarks.entries,
                SavedSitesNames.BOOKMARKS_ROOT,
                bookmarks.last_modified,
            )
        }

        // Favourites
        if (allResponseIds.contains(SavedSitesNames.FAVORITES_ROOT)) {
            Timber.d("Sync-Feature: favourites root found, traversing from there")
            processFavouritesFolder(conflictResolution, bookmarks.entries, bookmarks.last_modified)
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
            Timber.d("Sync-Feature: processing folder $folderId with parentId $parentId")
            Timber.d("Sync-Feature: can't find folder $folderId")
        } else {
            processBookmarkFolder(conflictResolution, remoteFolder, parentId, lastModified, processIds)
            remoteFolder.folder?.children?.forEach { child ->
                processBookmark(conflictResolution, child, processIds, remoteUpdates, folderId, lastModified)
            }
        }
    }

    private fun processBookmarkFolder(
        conflictResolution: SyncConflictResolution,
        remoteFolder: SyncBookmarkEntry,
        parentId: String,
        lastModified: String,
        processIds: MutableList<String>
    ) {
        val folder = decryptFolder(remoteFolder, parentId, lastModified)
        if (folder.id != SavedSitesNames.BOOKMARKS_ROOT && folder.id != SavedSitesNames.FAVORITES_ROOT) {
            Timber.d("Sync-Feature: processing folder ${folder.id} with parentId $parentId")
            when (conflictResolution) {
                DEDUPLICATION -> deduplicationStrategy.processBookmarkFolder(folder, parentId, lastModified)
                REMOTE_WINS -> remoteWinsStrategy.processBookmarkFolder(folder, parentId, lastModified)
                LOCAL_WINS -> localWinsStrategy.processBookmarkFolder(folder, parentId, lastModified)
                TIMESTAMP -> timestampStrategy.processBookmarkFolder(folder, parentId, lastModified)
            }
        }
        processIds.add(folder.id)
    }

    private fun processBookmark(
        conflictResolution: SyncConflictResolution,
        child: String,
        processIds: MutableList<String>,
        entries: List<SyncBookmarkEntry>,
        folderId: String,
        lastModified: String
    ) {
        Timber.d("Sync-Feature: processing child $child")
        processIds.add(child)
        val childEntry = entries.find { it.id == child }
        if (childEntry == null) {
            Timber.d("Sync-Feature: can't find child $child")
        } else {
            when {
                childEntry.isBookmark() -> {
                    Timber.d("Sync-Feature: child $child is a Bookmark")
                    val bookmark = decryptBookmark(childEntry, folderId, lastModified)
                    when (conflictResolution) {
                        DEDUPLICATION -> deduplicationStrategy.processBookmark(bookmark, child, entries, folderId, lastModified)
                        REMOTE_WINS -> remoteWinsStrategy.processBookmark(bookmark, child, entries, folderId, lastModified)
                        LOCAL_WINS -> localWinsStrategy.processBookmark(bookmark, child, entries, folderId, lastModified)
                        TIMESTAMP -> timestampStrategy.processBookmark(bookmark, child, entries, folderId, lastModified)
                    }
                }
                childEntry.isFolder() -> {
                    Timber.d("Sync-Feature: child $child is a Folder")
                    processFolder(childEntry.id, folderId, entries, lastModified, processIds, conflictResolution)
                }
            }
        }
    }

    private fun processFavouritesFolder(
        conflictResolution: SyncConflictResolution,
        entries: List<SyncBookmarkEntry>,
        lastModified: String
    ) {
        val favourites = entries.find { it.id == SavedSitesNames.FAVORITES_ROOT }!!
        favourites.folder?.children?.forEachIndexed { position, child ->
            Timber.d("Sync-Feature: child $child is a Favourite")
            val favouriteEntry = entries.find { it.id == child }
            if (favouriteEntry == null) {
                Timber.d("Sync-Feature: can't find favourite $child")
            } else {
                val favourite = decryptFavourite(favouriteEntry, position, lastModified)
                when (conflictResolution) {
                    DEDUPLICATION -> deduplicationStrategy.processFavourite(favourite, lastModified)
                    REMOTE_WINS -> remoteWinsStrategy.processFavourite(favourite, lastModified)
                    LOCAL_WINS -> localWinsStrategy.processFavourite(favourite, lastModified)
                    TIMESTAMP -> timestampStrategy.processFavourite(favourite, lastModified)
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
}
