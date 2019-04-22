/*
 * Copyright (c) 2017 DuckDuckGo
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
import com.duckduckgo.app.trackerdetection.model.DisconnectTracker


@Dao
abstract class TrackerDataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertAll(trackers: List<DisconnectTracker>)

    @Transaction
    open fun updateAll(trackers: List<DisconnectTracker>) {
        deleteAll()
        insertAll(trackers)
    }

    @Query("select * from disconnect_tracker")
    abstract fun getAll(): List<DisconnectTracker>

    @Query("select * from disconnect_tracker where url = :url")
    abstract fun get(url: String): DisconnectTracker?


    @Query("select count(*) from disconnect_tracker")
    abstract fun count(): Int

    @Query("delete from disconnect_tracker")
    abstract fun deleteAll()
}