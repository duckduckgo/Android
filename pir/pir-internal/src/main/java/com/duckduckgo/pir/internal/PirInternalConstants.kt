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

package com.duckduckgo.pir.internal

import com.duckduckgo.pir.internal.models.Address
import com.duckduckgo.pir.internal.models.ProfileQuery

object PirInternalConstants {
    val DEFAULT_PROFILE_QUERIES: List<ProfileQuery> = listOf(
        ProfileQuery(
            id = -1,
            firstName = "William",
            lastName = "Smith",
            city = "Chicago",
            state = "IL",
            addresses = listOf(
                Address(
                    city = "Chicago",
                    state = "IL",
                ),
            ),
            birthYear = 1993,
            fullName = "William Smith",
            age = 32,
            deprecated = false,
        ),
        ProfileQuery(
            id = -2,
            firstName = "Jane",
            lastName = "Doe",
            city = "New York",
            state = "NY",
            addresses = listOf(
                Address(
                    city = "New York",
                    state = "NY",
                ),
            ),
            birthYear = 1990,
            fullName = "Jane Doe",
            age = 35,
            deprecated = false,
        ),
        ProfileQuery(
            id = -3,
            firstName = "Alicia",
            lastName = "West",
            city = "Los Angeles",
            state = "CA",
            addresses = listOf(
                Address(
                    city = "Los Angeles",
                    state = "CA",
                ),
            ),
            birthYear = 1985,
            fullName = "Alicia West",
            age = 40,
            deprecated = false,
        ),
    )
}
