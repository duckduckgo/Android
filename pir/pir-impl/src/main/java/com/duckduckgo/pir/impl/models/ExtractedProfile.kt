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

package com.duckduckgo.pir.impl.models

/**
 * This class represents an extracted profile from a specific broker [brokerName] for a use
 * submitted profile [profileQueryId].
 *
 * [dbId] is the automatically generated id associated to the profile when stored locally.
 * Majority of the attributes here came from the [PirSuccessResponse.ExtractedResponse]'s
 * [ScriptExtractedProfile]. Note that all attributes from the script can be null so we don't use
 * any as the local db id.
 */
data class ExtractedProfile(
    val dbId: Long = 0L,
    val profileQueryId: Long,
    val brokerName: String,
    val name: String = "",
    val alternativeNames: List<String> = emptyList(),
    val age: String = "",
    val addresses: List<String> = emptyList(),
    val phoneNumbers: List<String> = emptyList(),
    val relatives: List<String> = emptyList(),
    val reportId: String = "",
    val email: String = "",
    val fullName: String = "",
    val profileUrl: String = "",
    val identifier: String = "",
    val dateAddedInMillis: Long = 0L,
    val deprecated: Boolean = false,
)
