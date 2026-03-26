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

package com.duckduckgo.pir.impl.store.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pir_user_profile")
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded(prefix = "user_")
    val userName: UserName,
    @Embedded(prefix = "address_")
    val addresses: Address,
    val birthYear: Int,
    val phone: String? = null,
    val deprecated: Boolean = false, // This should tell us if the profile is irrelevant for PIR (this is not me, profile edits)
)

data class UserName(
    val firstName: String,
    val lastName: String,
    val middleName: String? = null,
    val suffix: String? = null,
)

data class Address(
    val city: String,
    val state: String,
    val street: String? = null,
    val zip: String? = null,
)
