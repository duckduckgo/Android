/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.refreshpixels

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class RefreshDao {

    @Transaction
    open fun updateRecentRefreshes(minTime: Long, currentTime: Long): List<RefreshEntity> {
        insert(RefreshEntity(timestamp = currentTime))
        deleteInvalidRefreshes(minTime, currentTime)
        return all()
    }

    @Insert
    abstract fun insert(entity: RefreshEntity)

    @Query("DELETE FROM refreshes WHERE timestamp NOT BETWEEN :minTime AND :currentTime")
    abstract fun deleteInvalidRefreshes(minTime: Long, currentTime: Long)

    @Query("SELECT * FROM refreshes")
    abstract fun all(): List<RefreshEntity>
}
