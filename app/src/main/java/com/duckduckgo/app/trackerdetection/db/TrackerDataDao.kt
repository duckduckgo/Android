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

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import com.duckduckgo.app.trackerdetection.model.DisconnectTracker


@Dao
interface TrackerDataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(trackers: List<DisconnectTracker>)

    @Query("Select * from disconnect_tracker")
    fun getAll() : List<DisconnectTracker>

    @Query("Select count(*) from disconnect_tracker")
    fun count(): Int
}