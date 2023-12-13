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
    fun getFavorite(url: String, favoriteFolder: String): Favorite?

    /**
     * Returns a [Favorite] given a domain on specific Folder
     * @param id of the [Favorite]
     * @param favoriteFolder the folder to search
     * @return [Favorite] if found or null if not found
     */
    fun getFavoriteById(id: String, favoriteFolder: String): Favorite?

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
    fun insert(savedSite: SavedSite, favoriteFolder: String): SavedSite

    /**
     * Deletes a [SavedSite]
     * @param favoriteFolder which folder to delete from
     * @param savedSite to be deleted
     */
    fun delete(savedSite: SavedSite, favoriteFolder: String)

    /**
     * Updates the content of a [Favorite]
     * @param favoriteFolder which folder to update
     * @param savedSite to be updated
     */
    fun updateFavourite(favorite: Favorite, favoriteFolder: String)

    /**
     * Updates the position of [Favorite]
     * @param favoriteFolder which folder to update
     * @param favorites with all [Favorite]
     */
    fun updateWithPosition(favorites: List<Favorite>, favoriteFolder: String)

    /**
     * Replaces an existing [Favorite]
     * Used when syncing data from the backend
     * There are scenarios when a duplicate remote favourite has to be replace the local one
     * @param favorite the favourite to replace locally
     * @param favoriteFolder which folder to update
     * @param localId the local Id to be replaced
     */
    fun replaceFavourite(
        favorite: Favorite,
        localId: String,
        favoriteFolder: String,
    )

    /**
     * Returns the object needed for the sync request an existing [Favorite]
     * that represents the difference between remote and local state
     * @param folderId id of the folder to get the diff from
     */
    fun getFolderDiff(folderId: String): SyncFolderChildren

    /**
     * Moves children in request column to children.
     * This operation confirms that the BE and Client have the same children for a folder
     * All request columns will be affected
     */
    fun confirmAllFolderChildrenMetadata()

    /**
     * Stores the client children state for each folder before sending it to the Sync BE
     * @param folders list of folders to be stored
     */
    fun addRequestMetadata(folders: List<SyncSavedSitesRequestEntry>)

    /**
     * Stores the BE children state for each folder after receiving it
     * @param folders list of folders to be stored
     */
    fun addResponseMetadata(folders: List<SyncSavedSitesResponseEntry>)
}
