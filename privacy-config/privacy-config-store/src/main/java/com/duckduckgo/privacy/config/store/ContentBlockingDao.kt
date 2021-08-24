/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class ContentBlockingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAll(domains: List<ContentBlockingExceptionEntity>)

    @Transaction
    open fun updateAll(domains: List<ContentBlockingExceptionEntity>) {
        deleteAll()
        insertAll(domains)
    }

    @Query("select * from content_blocking_exceptions where domain = :domain")
    abstract fun get(domain: String): ContentBlockingExceptionEntity

    @Query("select * from content_blocking_exceptions")
    abstract fun getAll(): List<ContentBlockingExceptionEntity>

    @Query("delete from content_blocking_exceptions")
    abstract fun deleteAll()

    @Query("select count(*) from content_blocking_exceptions")
    abstract fun count(): Int

    @Query("select count(1) > 0 from content_blocking_exceptions where :domain LIKE '%'||domain||'%'")
    abstract fun contains(domain: String): Boolean
}
