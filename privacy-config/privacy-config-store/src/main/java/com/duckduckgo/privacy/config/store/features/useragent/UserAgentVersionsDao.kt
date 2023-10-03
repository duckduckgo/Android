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

package com.duckduckgo.privacy.config.store.features.useragent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.duckduckgo.privacy.config.store.UserAgentVersionsEntity

@Dao
abstract class UserAgentVersionsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAll(versions: List<UserAgentVersionsEntity>)

    @Transaction
    open fun updateAll(versions: List<UserAgentVersionsEntity>) {
        deleteAll()
        insertAll(versions)
    }

    @Query("select * from user_agent_versions where closestUserAgent = 1")
    abstract fun getClosestUserAgentVersions(): List<UserAgentVersionsEntity>

    @Query("select * from user_agent_versions where ddgFixedUserAgent = 1")
    abstract fun getDdgFixedUserAgentVerions(): List<UserAgentVersionsEntity>

    @Query("delete from user_agent_versions")
    abstract fun deleteAll()
}
