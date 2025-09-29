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

package com.duckduckgo.app.attributed.metrics.store

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "event_metrics",
    primaryKeys = ["eventName", "day"],
    indices = [Index("eventName"), Index("day")],
)
data class EventEntity(
    val eventName: String,
    val count: Int,
    // Format: YYYY-MM-DD
    val day: String,
)
