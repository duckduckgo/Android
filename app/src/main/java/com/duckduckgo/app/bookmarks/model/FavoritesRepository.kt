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

interface FavoritesRepository {
    suspend fun insert(favorite: Favorite): Long
}

data class Favorite(
    var title: String,
    var url: String
)

class FavoritesDataRepository (private val favoritesDao: FavoritesDao) : FavoritesRepository {

    override suspend fun insert(favorite: Favorite): Long {
        val lastPosition = favoritesDao.getLastPosition() ?: 0
        return favoritesDao.insert(FavoriteEntity(title = favorite.title, url = favorite.url, position = lastPosition + 1))
    }
}