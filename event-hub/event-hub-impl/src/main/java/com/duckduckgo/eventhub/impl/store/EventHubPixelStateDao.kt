/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.eventhub.impl.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EventHubPixelStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPixelState(entity: EventHubPixelStateEntity)

    @Query("SELECT * FROM event_hub_pixel_state WHERE pixelName = :name")
    fun getPixelState(name: String): EventHubPixelStateEntity?

    @Query("SELECT * FROM event_hub_pixel_state")
    fun getAllPixelStates(): List<EventHubPixelStateEntity>

    @Query("DELETE FROM event_hub_pixel_state WHERE pixelName = :name")
    fun deletePixelState(name: String)

    @Query("DELETE FROM event_hub_pixel_state")
    fun deleteAllPixelStates()
}
