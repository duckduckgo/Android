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

import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite

interface SyncSavedSitesRepository {

    /**
     * Returns all [Favorite] in the Database on specific Folder
     * @param favoriteFolder the folder to search
     * @return [List] of all [Favorite] in the Database
     */
    fun getFavoritesSync(favoriteFolder: String): List<Favorite>

    /**
     * Returns a [Favorite] given a domain on specific Folder
     * @param domain the url to filter
     * @param favoriteFolder the folder to search
     * @return [Favorite] if found or null if not found
     */
    fun getFavorite(
        url: String,
        favoriteFolder: String,
    ): Favorite?

    /**
     * Returns a [Favorite] given a domain on specific Folder
     * @param id of the [Favorite]
     * @param favoriteFolder the folder to search
     * @return [Favorite] if found or null if not found
     */
    fun getFavoriteById(
        id: String,
        favoriteFolder: String,
    ): Favorite?

    /**
     * Inserts a new [Favorite]
     * @param url of the site
     * @param title of the [Favorite]
     * @param favoriteFolder which folder to insert
     * @return [Favorite] inserted
     */
    fun insertFavorite(
        id: String = "",
        url: String,
        title: String,
        lastModified: String? = null,
        favoriteFolder: String,
    ): Favorite

    /**
     * Inserts a new [SavedSite]
     * @param favoriteFolder which folder to insert
     * @return [SavedSite] inserted
     */
    fun insert(
        savedSite: SavedSite,
        favoriteFolder: String,
    ): SavedSite

    /**
     * Replaces the existing [BookmarkFolder]
     * We remove all children stored locally and add the ones present in the [children] list
     * If ony of [children] was present in another folder, we take this opportunity
     * to remove it from that folder.
     * Doing it this way also updates the bookmark order to the correct one.
     */
    fun replaceBookmarkFolder(
        folder: BookmarkFolder,
        children: List<String>,
    )

    /**
     * Adds children to the existing [favouriteFolder]
     * Contrary to [replaceFavouriteFolder], we don't delete the children stored locally
     */
    fun addToFavouriteFolder(
        favouriteFolder: String,
        children: List<String>,
    )

    /**
     * Replaces the existing [favouriteFolder]
     * Contrary to [replaceBookmarkFolder], we don't delete the children from previous bookmark folder
     * This is because Favourites are special, and a bookmark is allowed to be in multiple favourite folders
     * and one bookmark folder
     * This also updates the bookmark order to the correct one
     */
    fun replaceFavouriteFolder(
        favouriteFolder: String,
        children: List<String>,
    )

    /**
     * Returns all [Bookmark] and [BookmarkFolder] inside a folder, also deleted objects
     * @param folderId the id of the folder.
     * @return [Pair] of [Bookmark] and [BookmarkFolder] inside a folder
     */
    fun getAllFolderContentSync(folderId: String): Pair<List<Bookmark>, List<BookmarkFolder>>

    /**
     * Replaces an existing [Bookmark]
     * Used when syncing data from the backend
     * There are scenarios when a duplicate remote bookmark has to be replace the local one
     * @param bookmark the bookmark to replace locally
     * @param localId the id of the local bookmark to be replaced
     */
    fun replaceBookmark(
        bookmark: Bookmark,
        localId: String,
    )

    /**
     * Returns the list of [BookmarkFolder] modified after [since]
     * @param since timestamp of modification for filtering
     * @return [List] of [BookmarkFolder]
     */
    fun getFoldersModifiedSince(since: String): List<BookmarkFolder>

    /**
     * Returns the list of [Bookmark] modified after [since]
     * @param since timestamp of modification for filtering
     * @return [List] of [Bookmark]
     */
    fun getBookmarksModifiedSince(since: String): List<Bookmark>

    /**
     * Returns the object needed for the sync request an existing [BookmarkFolder]
     * that represents the difference between remote and local state
     * @param folderId id of the folder to get the diff from
     */
    fun getFolderDiff(folderId: String): SyncFolderChildren

    /**
     * Stores the client children state for each folder before sending it to the Sync BE
     * @param folders list of folders to be stored
     */
    fun addRequestMetadata(folders: List<SyncSavedSitesRequestEntry>)

    /**
     * Discards the request metadata.
     * Client might need to call this method when sync engine didn't send changes to the server (e.g: first sync).
     */
    fun discardRequestMetadata()

    /**
     * Stores the BE children state for each folder after receiving it
     * @param entities list of entities received in the BE response
     */
    fun addResponseMetadata(entities: List<SyncSavedSitesResponseEntry>)

    /**
     * Deletes all existing metadata
     * This is called when Sync is disabled so all previous metadata is removed
     */
    fun removeMetadata()

    /**
     * Finds all the orphans (entities that don't belong to a folder)
     * and attached them to bookmarks root
     */
    fun fixOrphans(): Boolean

    /**
     * Deletes all entities with deleted = 1
     * This makes the deletion permanent
     */
    fun pruneDeleted()

    /**
     * Sets entities that were present in the device before a deduplication so
     * they are available for the next sync operation
     */
    fun setLocalEntitiesForNextSync(startTimestamp: String)

    /**
     * Returns the list of [SavedSite] that are marked as Invalid
     */
    fun getInvalidSavedSites(): List<SavedSite>

    /**
     * Marks as Invalid a list of [SavedSite] with the given ids
     */
    fun markSavedSitesAsInvalid(ids: List<String>)
}
