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

import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite

interface SyncSavedSitesRepository {

    /**
     * Returns all [Favorite] in the Database
     * @return [List] of all [Favorite] in the Database
     */
    fun getFavoritesSync(favoriteFolder: String): List<Favorite>

    /**
     * Returns a [Favorite] given a domain
     * @param domain the url to filter
     * @return [Favorite] if found or null if not found
     */
    fun getFavorite(url: String, favoriteFolder: String): Favorite?

    /**
     * Returns a [Favorite] given a domain
     * @param id of the [Favorite]
     * @return [Favorite] if found or null if not found
     */
    fun getFavoriteById(id: String, favoriteFolder: String): Favorite?

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
        favoriteFolder: String,
    ): Favorite

    /**
     * Inserts a new [SavedSite]
     * Used when undoing the deletion of a [Bookmark] or [Favorite]
     * @return [SavedSite] inserted
     */
    fun insert(savedSite: SavedSite, favoriteFolder: String): SavedSite

    /**
     * Deletes a [SavedSite]
     * @param savedSite to be deleted
     */
    fun delete(savedSite: SavedSite, favoriteFolder: String)

    /**
     * Updates the content of a [Favorite]
     * @param savedSite to be updated
     */
    fun updateFavourite(favorite: Favorite, favoriteFolder: String)

    /**
     * Updates the position of [Favorite]
     * Used when reordering [Favorite] in the QuickAccessPanel
     * @param favorites with all [Favorite]
     */
    fun updateWithPosition(favorites: List<Favorite>, favoriteFolder: String)

    /**
     * Replaces an existing [Favorite]
     * Used when syncing data from the backend
     * There are scenarios when a duplicate remote favourite has to be replace the local one
     * @param favorite the favourite to replace locally
     * @param localId the local Id to be replaced
     */
    fun replaceFavourite(
        favorite: Favorite,
        localId: String,
        favoriteFolder: String,
    )
}
