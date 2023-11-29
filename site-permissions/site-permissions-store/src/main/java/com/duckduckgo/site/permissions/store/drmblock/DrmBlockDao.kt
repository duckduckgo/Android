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

package com.duckduckgo.site.permissions.store.drmblock

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class DrmBlockDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAll(domains: List<DrmBlockExceptionEntity>)

    @Transaction
    open fun updateAll(domains: List<DrmBlockExceptionEntity>) {
        deleteAll()
        insertAll(domains)
    }

    @Query("select * from drm_block_exceptions")
    abstract fun getAll(): List<DrmBlockExceptionEntity>

    @Query("delete from drm_block_exceptions")
    abstract fun deleteAll()
}
