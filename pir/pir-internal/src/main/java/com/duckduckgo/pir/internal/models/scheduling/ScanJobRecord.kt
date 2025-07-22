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

package com.duckduckgo.pir.internal.models.scheduling

data class ScanJobRecord(
    val brokerName: String,
    val userProfileId: Long,
    val status: ScanJobStatus = ScanJobStatus.NOT_EXECUTED,
    val lastScanDateInMillis: Long? = null,
)

enum class ScanJobStatus {
    NOT_EXECUTED,
    NO_MATCH_FOUND, // Scan is completed and no extractedProfiles found
    MATCHES_FOUND, // Scan is completed and extractedProfiles were found
    ERROR, // Error encountered during the last scan job run
    INVALID, // The job is now invalid and should NOT be executed anymore.
}
