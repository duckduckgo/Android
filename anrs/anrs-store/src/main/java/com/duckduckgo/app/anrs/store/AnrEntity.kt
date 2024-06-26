/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.anrs.store

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "anr_entity")
data class AnrEntity(
    @PrimaryKey val hash: String,
    val message: String,
    val name: String,
    val file: String?,
    val lineNumber: Int,
    val stackTrace: List<String>,
    val timestamp: String,
    val webView: String,
    val customTab: Boolean,
)
