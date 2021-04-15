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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.Serializable

interface FavoritesRepository {
    suspend fun insert(unsavedSite: SavedSite.UnsavedSite): SavedSite.Favorite
    suspend fun insert(favorite: SavedSite.Favorite): SavedSite.Favorite
    suspend fun update(favorite: SavedSite.Favorite)
    suspend fun favorites(): Flow<List<SavedSite.Favorite>>
    suspend fun delete(favorite: SavedSite.Favorite)
}

sealed class SavedSite(
    open val id: Long,
    open val title: String,
    open val url: String
) : Serializable {
    data class Favorite(
        override val id: Long,
        override val title: String,
        override val url: String,
        val position: Int
    ) : SavedSite(id, title, url)

    data class Bookmark(
        override val id: Long,
        override val title: String,
        override val url: String
    ) : SavedSite(id, title, url)

    data class UnsavedSite(
        override val title: String,
        override val url: String
    ) : SavedSite(0, title, url)
}

class FavoritesDataRepository(private val favoritesDao: FavoritesDao) : FavoritesRepository {

    override suspend fun insert(favorite: SavedSite.UnsavedSite): SavedSite.Favorite {
        val lastPosition = favoritesDao.getLastPosition() ?: 0
        val favoriteEntity = FavoriteEntity(title = favorite.title, url = favorite.url, position = lastPosition + 1)
        val id = favoritesDao.insert(favoriteEntity)
        return SavedSite.Favorite(id, favoriteEntity.title, favoriteEntity.url, favoriteEntity.position)
    }

    override suspend fun insert(favorite: SavedSite.Favorite): SavedSite.Favorite {
        val favoriteEntity = FavoriteEntity(title = favorite.title, url = favorite.url, position = favorite.position)
        val id = favoritesDao.insert(favoriteEntity)
        return SavedSite.Favorite(id, favoriteEntity.title, favoriteEntity.url, favoriteEntity.position)
    }

    override suspend fun update(favorite: SavedSite.Favorite) {
        favoritesDao.update(FavoriteEntity(favorite.id, favorite.title, favorite.url, favorite.position))
    }

    override suspend fun favorites(): Flow<List<SavedSite.Favorite>> {
        return favoritesDao.favorites().map { favorites -> favorites.map { SavedSite.Favorite(it.id, it.title, it.url, it.position) } }
    }

    override suspend fun delete(favorite: SavedSite.Favorite) {
        favoritesDao.delete(FavoriteEntity(favorite.id, favorite.title, favorite.url, favorite.position))
    }
}
