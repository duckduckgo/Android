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

package com.duckduckgo.app.privacy.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.duckduckgo.app.privacy.model.UserAllowListedDomain
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UserAllowListDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(domain: UserAllowListedDomain)

    fun insert(domain: String) {
        insert(UserAllowListedDomain(domain))
    }

    @Delete
    abstract fun delete(domain: UserAllowListedDomain)

    fun delete(domain: String) {
        delete(UserAllowListedDomain(domain))
    }

    @Query("select * from user_whitelist")
    abstract fun all(): LiveData<List<UserAllowListedDomain>>

    @Query("select domain from user_whitelist")
    abstract fun allDomainsFlow(): Flow<List<String>>

    @Query("select count(1) > 0 from user_whitelist where domain = :domain")
    abstract fun contains(domain: String): Boolean
}
