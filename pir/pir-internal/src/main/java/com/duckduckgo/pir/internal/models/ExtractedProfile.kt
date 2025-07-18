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

package com.duckduckgo.pir.internal.models

import com.duckduckgo.pir.internal.scripts.models.AddressCityState

data class ExtractedProfile(
    val profileUrl: String,
    val profileQueryId: Long, // Unique identifier for the profileQuery
    val brokerName: String,
    val name: String? = null,
    val alternativeNames: List<String>? = emptyList(),
    val age: String? = null,
    val addresses: List<AddressCityState>? = emptyList(),
    val phoneNumbers: List<String>? = emptyList(),
    val relatives: List<String>? = emptyList(),
    val identifier: String? = null,
    val reportId: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val dateAddedInMillis: Long = 0L,
    val deprecated: Boolean = false,
)
