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

package com.duckduckgo.app.entities.db

import androidx.room.*

@Dao
abstract class EntityListDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAll(entities: List<EntityListEntity>)

    @Query("select * from entity_list")
    abstract fun getAll(): List<EntityListEntity>

    @Query("select * from entity_list where domainName=:domainName")
    abstract fun get(domainName: String): EntityListEntity?

    @Query("delete from entity_list")
    abstract fun deleteAll()

    @Query("select count(1) from entity_list")
    abstract fun count(): Int

    @Transaction
    open fun updateAll(entities: List<EntityListEntity>) {
        deleteAll()
        insertAll(entities)
    }

}