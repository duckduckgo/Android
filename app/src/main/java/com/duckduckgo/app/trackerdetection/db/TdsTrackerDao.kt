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
import com.duckduckgo.app.trackerdetection.model.TdsTracker

@Dao
abstract class TdsTrackerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAll(trackers: Collection<TdsTracker>)

    @Transaction
    open fun updateAll(trackers: Collection<TdsTracker>) {
        deleteAll()
        insertAll(trackers)
    }

    @Query("select * from tds_tracker") abstract fun getAll(): List<TdsTracker>

    @Query("select * from tds_tracker where domain = :domain")
    abstract fun get(domain: String): TdsTracker?

    @Query("select count(*) from tds_tracker") abstract fun count(): Int

    @Query("delete from tds_tracker") abstract fun deleteAll()
}
