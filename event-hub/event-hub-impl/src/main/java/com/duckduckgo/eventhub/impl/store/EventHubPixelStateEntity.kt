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

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-pixel runtime state.
 * [periodStartMillis] UTC timestamp when the current period began.
 * [periodEndMillis] UTC timestamp when the current period ends (fire time).
 * [paramsJson] parameter data as JSON, e.g. {"count": {"value": 5, "stopCounting": true}}
 * [configJson] snapshot of the per-pixel telemetry config at period start — used for all
 *   processing during the period so that mid-period config changes don't affect the running pixel.
 */
@Entity(tableName = "event_hub_pixel_state")
data class EventHubPixelStateEntity(
    @PrimaryKey val pixelName: String,
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val paramsJson: String,
    val configJson: String = "{}",
)
