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

package com.duckduckgo.webtelemetry.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface WebTelemetryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertConfig(entity: WebTelemetryConfigEntity)

    @Transaction
    fun updateConfig(entity: WebTelemetryConfigEntity) {
        deleteConfig()
        insertConfig(entity)
    }

    @Query("SELECT * FROM web_telemetry_config LIMIT 1")
    fun getConfig(): WebTelemetryConfigEntity?

    @Query("DELETE FROM web_telemetry_config")
    fun deleteConfig()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPixelState(entity: WebTelemetryPixelStateEntity)

    @Query("SELECT * FROM web_telemetry_pixel_state WHERE pixelName = :name")
    fun getPixelState(name: String): WebTelemetryPixelStateEntity?

    @Query("SELECT * FROM web_telemetry_pixel_state")
    fun getAllPixelStates(): List<WebTelemetryPixelStateEntity>

    @Query("DELETE FROM web_telemetry_pixel_state WHERE pixelName = :name")
    fun deletePixelState(name: String)

    @Query("DELETE FROM web_telemetry_pixel_state")
    fun deleteAllPixelStates()
}
