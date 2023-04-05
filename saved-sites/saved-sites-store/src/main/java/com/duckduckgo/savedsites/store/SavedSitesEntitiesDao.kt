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

package com.duckduckgo.savedsites.store

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import io.reactivex.Single
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedSitesEntitiesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: Entity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertList(entities: List<Entity>)

    @Query("select * from entities where deleted=0")
    fun entities(): List<Entity>

    @Query(
        "select * from entities inner join relations on entities.entityId = relations.entityId " +
            "and entities.type = :type and relations.folderId = :folderId and entities.deleted = 0",
    )
    fun entitiesInFolder(
        folderId: String,
        type: EntityType,
    ): List<Entity>

    @Query(
        "select * from entities inner join relations on entities.entityId = relations.entityId " +
            "where relations.folderId = :folderId and entities.deleted = 0",
    )
    fun entitiesInFolder(folderId: String): Flow<List<Entity>>

    @Query(
        "select * from entities inner join relations on entities.entityId = relations.entityId " +
            "where relations.folderId = :folderId and entities.deleted = 0",
    )
    fun entitiesInFolderSync(folderId: String): List<Entity>

    @Delete
    fun delete(entity: Entity)

    @Query("delete from entities where entityId = :id")
    fun delete(id: String)

    @Query("delete from entities where url = :url")
    fun deleteByUrl(url: String)

    @Delete
    fun deleteList(entities: List<Entity>)

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun update(entity: Entity)

    @Query("select * from entities where url = :url and entities.deleted = 0 limit 1")
    fun entityByUrl(url: String): Entity?

    @Query(
        "select count(*) from entities where entities.url and entities.deleted = 0 LIKE :domain",
    )
    fun countEntitiesByUrl(
        domain: String,
    ): Int

    @Query("select CAST(COUNT(*) AS BIT) from entities where entities.deleted = 0")
    fun hasEntities(): Boolean

    @Query("select * from entities where entityId = :id and entities.deleted = 0")
    fun entityById(id: String): Entity?

    @Query("select * from entities where title = :name and entities.deleted = 0")
    fun entityByName(name: String): Entity?

    @Query("select * from entities where type = :type and entities.deleted = 0")
    fun entitiesByType(type: EntityType): Flow<List<Entity>>

    @Query("select * from entities where type = :type and entities.deleted = 0")
    fun entitiesByTypeObservable(type: EntityType): Single<List<Entity>>

    @Query("select * from entities where type = :type and entities.deleted = 0")
    fun entitiesByTypeSync(type: EntityType): List<Entity>

    @Query("delete from entities where entityId != :bookmarksRoot AND entityId != :favoritesRoot")
    fun deleteAll(
        bookmarksRoot: String = SavedSitesNames.BOOMARKS_ROOT,
        favoritesRoot: String = SavedSitesNames.FAVORITES_ROOT,
    )
}
