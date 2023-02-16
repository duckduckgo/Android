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

package com.duckduckgo.sync.store

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncRelationsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(relation: Relation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertList(relations: List<Relation>)

    @Query("select * from relations")
    fun relations(): List<Relation>

    @Query("select * from entities inner join relations on entities.entityId = relations.entityId where relations.relationId = :folderId")
    fun relationById(folderId: String): Flow<List<Entity>>

    @Query("select * from relations where relations.entityId = :entityId")
    fun relationParentById(entityId: String): Relation

    @Query("select * from relations where relationId = :id")
    fun relationByIdSync(id: String): List<Relation>

    @Query("select CAST(COUNT(*) AS BIT) from relations")
    fun hasRelations(): Boolean

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(relation: Relation)

    @Delete
    fun delete(relation: Relation)

    @Query("delete from relations where relationId = :id")
    fun delete(id: String)

    @Query("delete from relations where entityId = :entityId")
    fun deleteEntity(entityId: String)

    @Query("select * from relations where type = :type")
    fun entitiesByType(type: EntityType): Flow<List<Relation>>
}
