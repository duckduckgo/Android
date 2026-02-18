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

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "web_telemetry_config")
data class WebTelemetryConfigEntity(
    @PrimaryKey val id: Int = 1,
    val json: String,
)

/**
 * Persists per-pixel runtime state.
 * [periodStartMillis] is the UTC timestamp when the current period began.
 * [paramsJson] stores parameter data as JSON, e.g. {"adwallCount": 5}
 */
@Entity(tableName = "web_telemetry_pixel_state")
data class WebTelemetryPixelStateEntity(
    @PrimaryKey val pixelName: String,
    val periodStartMillis: Long,
    val paramsJson: String,
)
