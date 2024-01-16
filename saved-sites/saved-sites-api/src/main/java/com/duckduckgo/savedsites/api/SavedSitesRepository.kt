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

package com.duckduckgo.savedsites.api

import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.BookmarkFolderItem
import com.duckduckgo.savedsites.api.models.FolderBranch
import com.duckduckgo.savedsites.api.models.FolderTreeItem
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSites
import io.reactivex.Single
import kotlinx.coroutines.flow.Flow

/**
 * The Repository that represents all CRUD operations related to [SavedSites]
 * There are two types os [SavedSites] can be [Favorite] or [Bookmark]
 */
interface SavedSitesRepository {

    /**
     * Returns all [SavedSites] inside a folder
     * @param folderId the id of the folder.
     * @return [Flow] of all [SavedSites]
     */
    fun getSavedSites(folderId: String): Flow<SavedSites>

    /**
     * Returns all [Bookmark] and [BookmarkFolder] inside a folder
     * @param folderId the id of the folder.
     * @return [FolderTreeItem]s inside a folder
     */
    fun getFolderTreeItems(folderId: String): List<FolderTreeItem>

    /**
     * Returns complete list of [BookmarkFolderItem] inside a folder. This method traverses all folders.
     * @param selectedFolderId the id of the folder.
     * @param currentFolder folder currently selected, used to determine the current depth in the tree.
     * @return [List] of [BookmarkFolderItem] inside a folder
     */
    fun getFolderTree(
        selectedFolderId: String,
        currentFolder: BookmarkFolder?,
    ): List<BookmarkFolderItem>

    /**
     * Returns complete list of all [Bookmark] This method traverses all folders.
     * @return [List] of all [Bookmark]
     */
    fun getBookmarksTree(): List<Bookmark>

    /**
     * Inserts all [Bookmark] and [BookmarkFolder] in a folder.
     * Used when Undoing [deleteFolderBranch]
     * @param branchToInsert the [FolderBranch] previously deleted
     * @return [FolderBranch] inserted
     */
    fun insertFolderBranch(branchToInsert: FolderBranch)

    /**
     * Deletes all [Bookmark] and [BookmarkFolder] inside a folder.
     * Used when Deleting a folder and its content
     * @param folder the [BookmarkFolder] to delete
     * @return [FolderBranch] deleted
     */
    fun deleteFolderBranch(folder: BookmarkFolder): FolderBranch

    /**
     * Returns the [FolderBranch] of a [BookmarkFolder]
     * @param folder the [BookmarkFolder] to retrieve
     * @return [FolderBranch] with [Bookmark] and [BookmarkFolder]
     */
    fun getFolderBranch(folder: BookmarkFolder): FolderBranch

    /**
     * Returns all [Bookmark] in the Database
     * @return [Flow] of all [Bookmark] in the Database
     */
    fun getBookmarks(): Flow<List<Bookmark>>

    /**
     * Returns all [Bookmark] in the Database
     * @return [Single] of all [Bookmark] in the Database
     */
    fun getBookmarksObservable(): Single<List<Bookmark>>

    /**
     * Returns [Bookmark] given a URL
     * @param url of the [Bookmark]
     * @return [Bookmark] if found, or null if does not exist
     */
    fun getBookmark(url: String): Bookmark?

    /**
     * Returns [Bookmark] given an ID
     * @param ID of the [Bookmark]
     * @return [Bookmark] if found, or null if does not exist
     */
    fun getBookmarkById(id: String): Bookmark?

    /**
     * Returns [SavedSite] given an ID
     * @param ID of the [SavedSite]
     * @return [SavedSite] if found, or null if does not exist
     */
    fun getSavedSite(id: String): SavedSite?

    /**
     * Returns all [Favorite] in the Database
     * @return [Flow] of all [Favorite] in the Database
     */
    fun getFavorites(): Flow<List<Favorite>>

    /**
     * Returns all [Favorite] in the Database
     * @return [Single] of all [Favorite] in the Database
     */
    fun getFavoritesObservable(): Single<List<Favorite>>

    /**
     * Returns all [Favorite] in the Database
     * @return [List] of all [Favorite] in the Database
     */
    fun getFavoritesSync(): List<Favorite>

    /**
     * Returns amount of [Favorite] given a domain
     * @param domain the url to filter
     * @return [Int] with the amount
     */
    fun getFavoritesCountByDomain(domain: String): Int

    /**
     * Returns a [Favorite] given a domain
     * @param domain the url to filter
     * @return [Favorite] if found or null if not found
     */
    fun getFavorite(url: String): Favorite?

    /**
     * Returns a [Favorite] given a domain
     * @param id of the [Favorite]
     * @return [Favorite] if found or null if not found
     */
    fun getFavoriteById(id: String): Favorite?

    /**
     * Returns if the user has any [Bookmark]
     * @return [Boolean] true if has [Bookmark], false if there are no [Bookmark]
     */
    fun hasBookmarks(): Boolean

    /**
     * Returns if the user has any [Favorite]
     * @return [Boolean] true if has [Favorite], false if there are no [Favorite]
     */
    fun hasFavorites(): Boolean

    /**
     * Inserts a new [Bookmark]
     * Used when adding a [Bookmark] from the Browser Menu
     * @param url of the site
     * @param title of the [Bookmark]
     * @return [Bookmark] inserted
     */
    fun insertBookmark(
        url: String,
        title: String,
    ): Bookmark

    /**
     * Inserts a new [Favorite]
     * Used when adding a [Favorite] from the Browser Menu
     * @param url of the site
     * @param title of the [Favorite]
     * @return [Favorite] inserted
     */
    fun insertFavorite(
        id: String = "",
        url: String,
        title: String,
        lastModified: String? = null,
    ): Favorite

    /**
     * Inserts a new [SavedSite]
     * Used when undoing the deletion of a [Bookmark] or [Favorite]
     * @return [SavedSite] inserted
     */
    fun insert(savedSite: SavedSite): SavedSite

    /**
     * Deletes a [SavedSite]
     * @param savedSite to be deleted
     */
    fun delete(savedSite: SavedSite, deleteBookmark: Boolean = false)

    /**
     * Updates the content of a [Favorite]
     * @param savedSite to be updated
     */
    fun updateFavourite(favorite: Favorite)

    /**
     * Updates the content of a [Bookmark]
     * @param savedSite to be updated
     * @param fromFolderId id of the previous bookmark folder
     * @param updateFavorite specifies whether the bookmark's favorite state has changed, default value is false
     */
    fun updateBookmark(
        bookmark: Bookmark,
        fromFolderId: String,
        updateFavorite: Boolean = false,
    )

    /**
     * Updates the position of [Favorite]
     * Used when reordering [Favorite] in the QuickAccessPanel
     * @param favorites with all [Favorite]
     */
    fun updateWithPosition(favorites: List<Favorite>)

    /**
     * Inserts a new [BookmarkFolder]
     * Used when adding a [BookmarkFolder] from the Bookmarks screen
     * @param folder to be added
     * @return [BookmarkFolder] inserted
     */
    fun insert(folder: BookmarkFolder): BookmarkFolder

    /**
     * Updates an existing [BookmarkFolder]
     * Used when updating a [BookmarkFolder] from the Bookmarks screen
     * @param folder to be added
     */
    fun update(folder: BookmarkFolder)

    /**
     * Deletes an existing [BookmarkFolder]
     * Used when deleting a [BookmarkFolder] from the Bookmarks screen
     * @param folder to be added
     */
    fun delete(folder: BookmarkFolder)

    /**
     * Replaces an existing [BookmarkFolder]
     * Used when syncing data from the backend
     * There are scenarios when a duplicate remote folder has to be replace the local one
     * @param folder the folder that will replace [localId]
     * @param localId the id of the local folder to be replaced
     */
    fun replaceFolderContent(folder: BookmarkFolder, oldId: String)

    /**
     * Returns a [BookmarkFolder] based on its id
     * @param folderId of the [BookmarkFolder]
     * @return [BookmarkFolder] if exists, or null if doesn't
     */
    fun getFolder(folderId: String): BookmarkFolder?

    /**
     * Returns a [BookmarkFolder] based on its name
     * @param folderId of the [BookmarkFolder]
     * @return [BookmarkFolder] if exists, or null if doesn't
     */
    fun getFolderByName(folderName: String): BookmarkFolder?

    /**
     * Deletes all [SavedSites]
     */
    fun deleteAll()

    /**
     * Returns total number of [Bookmark]
     * @return [Long] with total amount of [Bookmark]
     */
    fun bookmarksCount(): Long

    /**
     * Returns total number of [Favorite]
     * @return [Long] with total amount of [Favorite]
     */
    fun favoritesCount(): Long

    /**
     * Returns the id the last modified [SavedSite]
     * @return [Flow] of [String]
     */
    fun lastModified(): Flow<String>

    /**
     * Deletes all entities with deleted = 1
     * This makes the deletion permanent
     */
    fun pruneDeleted()

    /**
     * Deletes and re-inserts a folder relation
     */
    fun updateFolderRelation(folderId: String, entities: List<String>)
}
