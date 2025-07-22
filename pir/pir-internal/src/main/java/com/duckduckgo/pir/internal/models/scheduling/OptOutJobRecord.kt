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

data class OptOutJobRecord(
    val extractedProfileId: Long, // Identifier for which ExtractedProfile the state is for
    val brokerName: String,
    val userProfileId: Long,
    val status: OptOutJobStatus = OptOutJobStatus.NOT_EXECUTED,
    val attemptCount: Int = 0,
    val lastOptOutAttemptDate: Long? = null,
    val optOutRequestedDate: Long = 0L, // Date when the opt-out for the record has been successfully submitted.
    val optOutRemovedDate: Long = 0L, // Date when we confirmed that the record has been successfully removed from the broker.
)

enum class OptOutJobStatus {
    NOT_EXECUTED,
    REQUESTED, // The opt out job has successfully sent a request for removal.
    REMOVED, // The profile has been successfully removed.
    ERROR, // The opt out job has failed to send a request for removal.
    INVALID, // The job is now invalid and should NOT be executed anymore.
}
