/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.bookmarks.model

import com.duckduckgo.app.bookmarks.db.FavoriteEntity
import com.duckduckgo.app.bookmarks.db.FavoritesDao
import com.duckduckgo.app.browser.favicon.FaviconManager
import dagger.Lazy
import io.reactivex.Single
import java.io.Serializable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface FavoritesRepository {
    fun favoritesCountByDomain(domain: String): Int
    fun favoritesObservable(): Single<List<SavedSite.Favorite>>
    fun favoritesSync(): List<SavedSite.Favorite>
    fun insert(title: String, url: String): SavedSite.Favorite
    fun insert(favorite: SavedSite.Favorite)
    fun update(favorite: SavedSite.Favorite)
    fun updateWithPosition(favorites: List<SavedSite.Favorite>)
    fun favorites(): Flow<List<SavedSite.Favorite>>
    fun userHasFavorites(): Boolean
    fun favorite(url: String): SavedSite.Favorite?
    suspend fun delete(favorite: SavedSite.Favorite)
}

sealed class SavedSite(open val id: Long, open val title: String, open val url: String) :
    Serializable {
    data class Favorite(
        override val id: Long,
        override val title: String,
        override val url: String,
        val position: Int
    ) : SavedSite(id, title, url)

    data class Bookmark(
        override val id: Long,
        override val title: String,
        override val url: String,
        val parentId: Long
    ) : SavedSite(id, title, url)
}

class FavoritesDataRepository(
    private val favoritesDao: FavoritesDao,
    private val faviconManager: Lazy<FaviconManager>,
) : FavoritesRepository {
    override fun favoritesCountByDomain(domain: String): Int {
        return favoritesDao.favoritesCountByUrl(domain)
    }

    override fun favoritesObservable() =
        favoritesDao.favoritesObservable().map { favorites -> favorites.mapToSavedSites() }

    override fun favoritesSync() = favoritesDao.favoritesSync().mapToSavedSites()

    override fun insert(title: String, url: String): SavedSite.Favorite {
        val titleOrFallback = title.takeIf { it.isNotEmpty() } ?: url
        val lastPosition = favoritesDao.getLastPosition() ?: 0
        val favoriteEntity =
            FavoriteEntity(title = titleOrFallback, url = url, position = lastPosition + 1)
        val id = favoritesDao.insert(favoriteEntity)
        return SavedSite.Favorite(
            id, favoriteEntity.title, favoriteEntity.url, favoriteEntity.position)
    }

    override fun insert(favorite: SavedSite.Favorite) {
        if (favorite.url.isEmpty()) return
        val favoriteEntity =
            FavoriteEntity(
                title = favorite.titleOrFallback(),
                url = favorite.url,
                position = favorite.position)
        favoritesDao.insert(favoriteEntity)
    }

    override fun update(favorite: SavedSite.Favorite) {
        if (favorite.url.isEmpty()) return
        favoritesDao.update(
            FavoriteEntity(
                favorite.id, favorite.titleOrFallback(), favorite.url, favorite.position))
    }

    override fun updateWithPosition(favorites: List<SavedSite.Favorite>) {
        favoritesDao.persistChanges(favorites)
    }

    override fun favorites(): Flow<List<SavedSite.Favorite>> {
        return favoritesDao.favorites().distinctUntilChanged().map { favorites ->
            favorites.mapToSavedSites()
        }
    }

    override fun userHasFavorites(): Boolean {
        return favoritesDao.userHasFavorites()
    }

    override fun favorite(url: String): SavedSite.Favorite? {
        return favoritesDao.favoriteByUrl(url)?.mapToSavedSite()
    }

    override suspend fun delete(favorite: SavedSite.Favorite) {
        faviconManager.get().deletePersistedFavicon(favorite.url)
        favoritesDao.delete(
            FavoriteEntity(favorite.id, favorite.title, favorite.url, favorite.position))
    }

    private fun SavedSite.Favorite.titleOrFallback(): String =
        this.title.takeIf { it.isNotEmpty() } ?: this.url

    private fun FavoriteEntity.mapToSavedSite(): SavedSite.Favorite =
        SavedSite.Favorite(this.id, this.title, this.url, this.position)

    private fun List<FavoriteEntity>.mapToSavedSites(): List<SavedSite.Favorite> =
        this.map { SavedSite.Favorite(it.id, it.title, it.url, it.position) }
}
