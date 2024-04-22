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
import androidx.room.Transaction
import androidx.room.Update
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.store.EntityType.FOLDER
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

    @Query("select * from entities where entities.entityId IN (:ids)")
    fun entities(ids: List<String>): List<Entity>

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

    @Query(
        "select * from entities inner join relations on entities.entityId = relations.entityId " +
            "where relations.folderId = :folderId",
    )
    fun allEntitiesInFolderSync(folderId: String): List<Entity>

    @Query("update entities set title = '', url = '', deleted = 1, lastModified = :lastModified where entityId = :id")
    fun delete(
        id: String,
        lastModified: String = DatabaseDateFormatter.iso8601(),
    )

    @Query("delete from entities")
    fun deleteAll()

    @Delete
    fun deletePermanently(entity: Entity)

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun update(entity: Entity)

    @Query("update entities set entityId = :newId where entityId = :oldId")
    fun updateId(
        oldId: String,
        newId: String,
    )

    @Transaction
    fun updateModified(entities: List<String>, lastModified: String = DatabaseDateFormatter.iso8601()) {
        entities.forEach {
            updateModified(it, lastModified)
        }
    }

    @Query("update entities set lastModified = :lastModified where entityId = :entityId")
    fun updateModified(
        entityId: String,
        lastModified: String = DatabaseDateFormatter.iso8601(),
    )

    @Query("select * from entities limit 1")
    fun lastModified(): Flow<Entity>

    @Query("select * from entities where url = :url and entities.deleted = 0 limit 1")
    fun entityByUrl(url: String): Entity?

    @Query(
        "select count(*) from entities where entities.url LIKE :domain and entities.deleted = 0 ",
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

    @Query("select * from entities where type = :type")
    fun allEntitiesByTypeSync(type: EntityType): List<Entity>

    @Query("select * from entities where entityId = :id")
    fun deletedEntity(id: String): Entity?

    @Query("select * from entities where deleted = 1")
    fun allDeleted(): List<Entity>

    @Query("select * from entities inner join relations on entities.entityId = relations.entityId and relations.folderId <> :favoritesRoot")
    fun allBookmarks(favoritesRoot: String = SavedSitesNames.FAVORITES_ROOT): List<Entity>

    @Transaction
    fun createFormFactorFavoriteFolders() {
        val rootFolder = entityById(SavedSitesNames.FAVORITES_ROOT)
        if (rootFolder == null) {
            insert(Entity(SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_NAME, "", FOLDER, lastModified = null))
        }
        val rootFolderLastModified = rootFolder?.lastModified ?: DatabaseDateFormatter.iso8601()
        if (entityById(SavedSitesNames.FAVORITES_MOBILE_ROOT) == null) {
            insert(
                Entity(
                    SavedSitesNames.FAVORITES_MOBILE_ROOT,
                    SavedSitesNames.FAVORITES_MOBILE_NAME,
                    "",
                    FOLDER,
                    lastModified = rootFolderLastModified,
                ),
            )
        }
        if (entityById(SavedSitesNames.FAVORITES_DESKTOP_ROOT) == null) {
            insert(Entity(SavedSitesNames.FAVORITES_DESKTOP_ROOT, SavedSitesNames.FAVORITES_DESKTOP_NAME, "", FOLDER, lastModified = null))
        }
    }

    @Transaction
    fun removeFormFactorFavoriteFolders() {
        entityById(SavedSitesNames.FAVORITES_MOBILE_ROOT)?.let {
            deletePermanently(it)
        }
        entityById(SavedSitesNames.FAVORITES_DESKTOP_ROOT)?.let {
            deletePermanently(it)
        }
    }
}
