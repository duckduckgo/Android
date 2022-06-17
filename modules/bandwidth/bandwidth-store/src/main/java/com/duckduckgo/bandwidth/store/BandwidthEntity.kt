/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.bandwidth.store

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bandwidth")
data class BandwidthEntity(
    // There will only ever be one entry in this table, so the ID is always 0.
    @PrimaryKey val id: Int = 0,
    val timestamp: Long,
    val appBytes: Long,
    val totalBytes: Long
)
