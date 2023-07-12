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

package com.duckduckgo.autofill.store.sync

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LoginCredentialsSyncDao {

    @Query("select * from website_login_credentials_sync_meta where id = :id")
    fun getSyncId(id: Long): LoginCredentialsSync?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: LoginCredentialsSync): Long

    @Delete
    fun delete(entity: LoginCredentialsSync)
}
