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
import com.duckduckgo.privacy.config.store.UserAgentStatesEntity

@Dao
abstract class UserAgentStatesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(states: UserAgentStatesEntity)

    @Transaction
    open fun update(states: UserAgentStatesEntity) {
        delete()
        insert(states)
    }

    @Query("select * from user_agent_states")
    abstract fun get(): UserAgentStatesEntity?

    @Query("delete from user_agent_states")
    abstract fun delete()
}
