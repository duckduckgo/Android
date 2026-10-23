/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.db

import androidx.room.*
import com.duckduckgo.app.trackerdetection.model.TdsCnameEntity

@Dao
abstract class TdsCnameEntityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAll(entities: List<TdsCnameEntity>)

    @Query("select * from tds_cname_entity")
    abstract fun getAll(): List<TdsCnameEntity>

    @Query("select * from tds_cname_entity where cloakedHostName=:cloakedHostName")
    abstract fun get(cloakedHostName: String): TdsCnameEntity?

    @Query("delete from tds_cname_entity")
    abstract fun deleteAll()

    @Query("select count(*) from tds_cname_entity")
    abstract fun count(): Int

    @Transaction
    open fun updateAll(entities: List<TdsCnameEntity>) {
        deleteAll()
        insertAll(entities)
    }
}
