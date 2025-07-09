/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.store.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WebsiteLoginCredentialsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(loginCredentials: WebsiteLoginCredentialsEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(loginCredentials: List<WebsiteLoginCredentialsEntity>): List<Long>

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(loginCredentials: WebsiteLoginCredentialsEntity)

    @Query("select * from website_login_credentials")
    fun websiteLoginCredentials(): Flow<List<WebsiteLoginCredentialsEntity>>

    @Query("select * from website_login_credentials where domain like '%' || :domain || '%'")
    fun websiteLoginCredentialsByDomain(domain: String): Flow<List<WebsiteLoginCredentialsEntity>>

    @Query("select * from website_login_credentials where id = :id")
    fun getWebsiteLoginCredentialsById(id: Long): WebsiteLoginCredentialsEntity?

    @Delete
    fun delete(loginCredentials: WebsiteLoginCredentialsEntity)

    @Query("DELETE FROM website_login_credentials WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM website_login_credentials WHERE id IN (:ids)")
    fun delete(ids: List<Long>)

    @Query("select * from website_login_credentials where domain is null OR domain = ''")
    fun websiteLoginCredentialsWithoutDomain(): Flow<List<WebsiteLoginCredentialsEntity>>
}
