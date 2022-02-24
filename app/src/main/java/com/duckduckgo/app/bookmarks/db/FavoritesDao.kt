/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.bookmarks.db

import androidx.room.*
import com.duckduckgo.app.bookmarks.model.SavedSite
import io.reactivex.Single
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(favorite: FavoriteEntity): Long

    @Query("select * from favorites order by position")
    fun favorites(): Flow<List<FavoriteEntity>>

    @Query("select * from favorites order by position")
    fun favoritesSync(): List<FavoriteEntity>

    @Query("select count(1) > 0 from favorites")
    fun userHasFavorites(): Boolean

    @Query("select count(*) from favorites WHERE url LIKE :domain")
    fun favoritesCountByUrl(domain: String): Int

    @Query("select count(*) from favorites")
    fun favoritesCount(): Long

    @Delete
    fun delete(favorite: FavoriteEntity)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(favoriteEntity: FavoriteEntity)

    @Query("select * from favorites")
    fun favoritesObservable(): Single<List<FavoriteEntity>>

    @Query("select position from favorites where position = ( select MAX(position) from favorites)")
    fun getLastPosition(): Int?

    @Query("select * from favorites where id = :id")
    fun favorite(id: Long): FavoriteEntity?

    @Query("select * from favorites where url = :url limit 1")
    fun favoriteByUrl(url: String): FavoriteEntity?

    @Transaction
    fun persistChanges(favorites: List<SavedSite.Favorite>) {
        favorites.forEachIndexed { index, favorite ->
            val favoriteEntity = favorite(favorite.id) ?: return
            favoriteEntity.position = index
            update(favoriteEntity)
        }
    }
}
