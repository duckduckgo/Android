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

package com.duckduckgo.pir.internal.scripts.models

/**
 * This profile represents the data we can get from the web UI / from the user
 */
data class ProfileQuery(
    val id: Long? = null,
    val firstName: String,
    val lastName: String,
    val middleName: String? = null,
    val suffix: String? = null,
    val city: String,
    val state: String,
    val street: String? = null,
    val zip: String? = null,
    val addresses: List<Address>,
    val birthYear: Int,
    val phone: String? = null,
    val fullName: String,
    val age: Int,
    val deprecated: Boolean,
)

data class Address(
    val city: String,
    val state: String,
)

data class ExtractedProfile(
    val id: Int? = null,
    val name: String? = null,
    val alternativeNames: List<String>? = emptyList(),
    val age: String? = null,
    val addresses: List<AddressCityState>? = emptyList(),
    val phoneNumbers: List<String>? = emptyList(),
    val relatives: List<String>? = emptyList(),
    val profileUrl: String? = null,
    val identifier: String? = null,
    val reportId: String? = null,
    val email: String? = null,
    val removedDate: String? = null,
    val fullName: String? = null,
)

data class AddressCityState(
    val city: String,
    val state: String,
    val fullAddress: String? = null,
)
