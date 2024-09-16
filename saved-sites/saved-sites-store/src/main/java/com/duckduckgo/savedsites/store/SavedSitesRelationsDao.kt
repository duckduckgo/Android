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
import com.duckduckgo.savedsites.api.models.*
import com.duckduckgo.savedsites.api.models.SavedSitesNames.BOOKMARKS_ROOT
import com.duckduckgo.savedsites.api.models.SavedSitesNames.FAVORITES_DESKTOP_ROOT
import com.duckduckgo.savedsites.api.models.SavedSitesNames.FAVORITES_MOBILE_ROOT
import com.duckduckgo.savedsites.api.models.SavedSitesNames.FAVORITES_ROOT
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
    fun relationsByFolderId(folderId: String): List<Relation>

    @Query("select * from relations where folderId =:folderId")
    fun relationsObservable(folderId: String): Single<List<Relation>>

    @Query("update relations set folderId = :newId where folderId = :oldId")
    fun updateFolderId(
        oldId: String,
        newId: String,
    )

    @Query("update relations set entityId = :newId where entityId = :oldId")
    fun updateEntityId(
        oldId: String,
        newId: String,
    )

    @Query(
        "select count(*) from entities inner join relations on entities.entityId = relations.entityId " +
            "and entities.type = :type and relations.folderId = :folderId and entities.deleted = 0",
    )
    fun countEntitiesInFolder(
        folderId: String,
        type: EntityType,
    ): Int

    @Query(
        "select * from relations where relations.entityId = :entityId and relations.folderId <> '$FAVORITES_MOBILE_ROOT' " +
            "and relations.folderId <> '$FAVORITES_DESKTOP_ROOT' and relations.folderId <> '$FAVORITES_ROOT'",
    )
    fun relationByEntityId(entityId: String): Relation?

    @Query(
        "select * from relations where relations.entityId = :entityId and relations.folderId = :folderId",
    )
    fun relation(folderId: String, entityId: String): Relation?

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
    fun deleteRelationByEntityAndFolder(
        entityId: String,
        folderId: String,
    )

    @Query("delete from relations where entityId = :entityId AND folderId = :folderId")
    fun deleteRelationByEntity(
        entityId: String,
        folderId: String,
    )

    @Query(
        "delete from relations where relations.entityId = :entityId and relations.folderId <> '$FAVORITES_MOBILE_ROOT' " +
            "and relations.folderId <> '$FAVORITES_DESKTOP_ROOT' and relations.folderId <> '$FAVORITES_ROOT'",
    )
    fun deleteOldRelationsByEntity(entityId: String)

    @Query(
        "select count(*) from entities inner join relations on entities.entityId = relations.entityId " +
            "where entities.url LIKE '%' || :domain AND folderId == :folderId AND entities.deleted = 0",
    )
    fun countFavouritesByUrl(
        domain: String,
        folderId: String,
    ): Int

    @Query("delete from relations")
    fun deleteAll()

    @Transaction
    fun migrateNativeFavoritesAsNewRoot() {
        // clear non-native favorites
        delete(SavedSitesNames.FAVORITES_DESKTOP_ROOT)
        // clear unified folder
        delete(SavedSitesNames.FAVORITES_ROOT)
        // add all native favorites to unified folder
        updateFolderId(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_ROOT)
    }

    @Transaction
    fun cloneFolder(
        from: String,
        to: String,
    ) {
        delete(to)
        copyRelationsFromTo(from, to)
    }

    @Query("INSERT INTO relations (folderId, entityId) Select :to, entityId from relations where folderId = :from")
    fun copyRelationsFromTo(
        from: String,
        to: String,
    )

    @Transaction
    fun migrateUnifiedFavoritesAsNewRoot() {
        // clear non-native folder
        delete(SavedSitesNames.FAVORITES_DESKTOP_ROOT)
        // clear native folder
        delete(SavedSitesNames.FAVORITES_MOBILE_ROOT)
    }

    @Query(
        "select * from entities WHERE entities.entityId NOT IN " +
            "(select relations.entityId FROM relations WHERE relations.folderId <> '$FAVORITES_ROOT' " +
            "AND relations.folderId<> '$FAVORITES_DESKTOP_ROOT' AND relations.folderId <> '$FAVORITES_MOBILE_ROOT')",
    )
    fun entitiesWithoutRelation(): List<Entity>

    fun getOrphans(): List<Entity> {
        // orphan = entities which id is not entityId in table relations
        return entitiesWithoutRelation().filter {
            it.entityId != BOOKMARKS_ROOT && it.entityId != FAVORITES_MOBILE_ROOT &&
                it.entityId != FAVORITES_DESKTOP_ROOT && it.entityId != FAVORITES_ROOT
        }
    }

    @Transaction
    fun replaceBookmarkFolder(
        folder: String,
        children: List<String>,
    ) {
        delete(folderId = folder)
        children.forEach {
            // before inserting the new relation, we remove old relations from this entity, but not the ones from favourites root
            deleteOldRelationsByEntity(it)
            insert(Relation(folderId = folder, entityId = it))
        }
        updateModified(folder)
    }

    @Transaction
    fun replaceFavouriteFolder(
        favouriteFolder: String,
        children: List<String>,
    ) {
        delete(folderId = favouriteFolder)
        children.forEach {
            insert(Relation(folderId = favouriteFolder, entityId = it))
        }
    }

    @Query("update entities set lastModified = :lastModified where entityId = :entityId")
    fun updateModified(
        entityId: String,
        lastModified: String = DatabaseDateFormatter.iso8601(),
    )
}
