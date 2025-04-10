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

/**
 * Contains the sites that have been scanned.
 * Scanned means that the scan flow has been started and completed for the broker.
 */
@Entity(tableName = "pir_scan_complete_brokers")
data class ScanCompletedBroker(
    @PrimaryKey val brokerName: String,
    val startTimeInMillis: Long,
    val endTimeInMillis: Long,
)

/**
 * Contains all the extracted profile result from a scan run.
 * To know if an extracted profile exists for a broker, we need to check the extractResults and should not be empty.
 */
@Entity(tableName = "pir_scan_extracted_profile")
data class ExtractProfileResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val brokerName: String,
    val completionTimeInMillis: Long,
    val actionType: String,
    val userData: String,
    val extractResults: List<String>,
)

/**
 * This table contains any navigation result (both for scan and opt-out).
 * This is mostly for logging purpose.
 */
@Entity(tableName = "pir_scan_navigate_results")
data class ScanNavigateResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val brokerName: String,
    val actionType: String,
    val url: String,
    val completionTimeInMillis: Long,
)

/**
 * This table contains ALL error result from any action (regardless if it is from scan and opt-out).
 * This is mostly for logging purpose.
 */
@Entity(tableName = "pir_scan_error")
data class ScanErrorResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val brokerName: String,
    val completionTimeInMillis: Long,
    val actionType: String,
    val message: String,
)
