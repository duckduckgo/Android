/*
 * Copyright (c) 2019 DuckDuckGo
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
import com.duckduckgo.app.trackerdetection.model.TdsDomainEntity

@Dao
abstract class TdsDomainEntityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAll(entities: List<TdsDomainEntity>)

    @Query("select * from tds_domain_entity")
    abstract fun getAll(): List<TdsDomainEntity>

    @Query("select * from tds_domain_entity where domain=:domain")
    abstract fun get(domain: String): TdsDomainEntity?

    @Query("delete from tds_domain_entity")
    abstract fun deleteAll()

    @Query("select count(*) from tds_domain_entity")
    abstract fun count(): Int

    @Transaction
    open fun updateAll(entities: List<TdsDomainEntity>) {
        deleteAll()
        insertAll(entities)
    }

}
