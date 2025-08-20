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

data class ProfileQuery(
    val id: Long,
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
