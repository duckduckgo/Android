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
@Entity(
    tableName = "pir_scan_complete_brokers",
    primaryKeys = ["brokerName", "profileQueryId"],
)
data class ScanCompletedBroker(
    val brokerName: String,
    val profileQueryId: Long,
    val startTimeInMillis: Long,
    val endTimeInMillis: Long,
    val isSuccess: Boolean,
)

@Entity(tableName = "pir_extracted_profiles")
data class StoredExtractedProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileQueryId: Long, // Unique identifier for the profileQuery
    val brokerName: String, // Unique identifier for broker in which the profile was found
    val name: String? = null,
    val alternativeNames: List<String> = emptyList(),
    val age: String? = null,
    val addresses: List<String> = emptyList(),
    val phoneNumbers: List<String> = emptyList(),
    val relatives: List<String> = emptyList(),
    val profileUrl: String? = null, // This can be null for some brokers
    val identifier: String? = null, // This can be null for some brokers
    val reportId: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val dateAddedInMillis: Long = 0L, // Tells us when the extracted profile has been found
    val deprecated: Boolean = false, // This should tell us if the profile is irrelevant for PIR (this is not me, profile edits)
)
