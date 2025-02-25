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

package com.duckduckgo.pir.internal.store.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pir_scan_navigate_results")
data class ScanNavigateResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val brokerName: String,
    val actionType: String,
    val url: String,
    val completionTimeInMillis: Long,
)

@Entity(tableName = "pir_scan_error")
data class ScanErrorResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val brokerName: String,
    val completionTimeInMillis: Long,
    val actionType: String,
    val message: String,
)

@Entity(tableName = "pir_scan_extracted_profile")
data class ExtractProfileResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val brokerName: String,
    val completionTimeInMillis: Long,
    val actionType: String,
    val userData: String,
    val extractResults: List<String>,
)
