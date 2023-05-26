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
interface SavedSitesRelationsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(relation: Relation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertList(relations: List<Relation>)

    @Query("select * from relations")
    fun relations(): List<Relation>

    @Query("select * from relations where folderId =:folderId")
    fun relations(folderId: String): Flow<List<Relation>>

    @Query("select * from relations where folderId =:folderId")
    fun relationsObservable(folderId: String): Single<List<Relation>>

    @Query(
        "select count(*) from entities inner join relations on entities.entityId = relations.entityId " +
            "and entities.type = :type and relations.folderId = :folderId and entities.deleted = 0",
    )
    fun countEntitiesInFolder(
        folderId: String,
        type: EntityType,
    ): Int

    @Query("select * from relations where relations.entityId = :entityId and relations.folderId <> :favoritesRoot")
    fun relationByEntityId(entityId: String, favoritesRoot: String = SavedSitesNames.FAVORITES_ROOT): Relation?

    @Query("select * from relations where relations.entityId = :entityId")
    fun relationsByEntityId(entityId: String): List<Relation>

    @Query("select CAST(COUNT(*) AS BIT) from relations")
    fun hasRelations(): Boolean

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(relation: Relation)

    @Delete
    fun delete(relation: Relation)

    @Query("delete from relations where folderId = :folderId")
    fun delete(folderId: String)

    @Query("delete from relations where entityId = :entityId")
    fun deleteRelationByEntity(entityId: String)

    @Query("delete from relations where relations.entityId = :entityId and relations.folderId = :folderId")
    fun deleteRelationByEntityAndFolder(entityId: String, folderId: String)

    @Query("delete from relations where entityId = :entityId AND folderId = :folderId")
    fun deleteRelationByEntity(
        entityId: String,
        folderId: String,
    )

    @Query(
        "select count(*) from entities inner join relations on entities.entityId = relations.entityId " +
            "where entities.url LIKE :domain AND folderId == :folderId AND entities.deleted = 0",
    )
    fun countFavouritesByUrl(
        domain: String,
        folderId: String = SavedSitesNames.FAVORITES_ROOT,
    ): Int

    @Query("delete from relations")
    fun deleteAll()
}
