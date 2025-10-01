
/*
 * Copyright (c) 2022 DuckDuckGo
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
@file:Suppress("ktlint:standard:filename")

package com.duckduckgo.cookies.store

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.duckduckgo.feature.toggles.api.FeatureException

@Entity(tableName = "first_party_cookie_policy")
data class FirstPartyCookiePolicyEntity(
    @PrimaryKey val id: Int = 1,
    val threshold: Int,
    val maxAge: Int,
)

@Entity(tableName = "cookie_exceptions")
data class CookieExceptionEntity(
    @PrimaryKey val domain: String,
    val reason: String,
)

@Entity(tableName = "cookie")
data class CookieEntity(
    @PrimaryKey val id: Int = 1,
    val json: String,
)

@Entity(tableName = "third_party_cookie_names")
data class CookieNamesEntity(
    @PrimaryKey val name: String,
)

fun CookieExceptionEntity.toFeatureException(): FeatureException {
    return FeatureException(domain = this.domain, reason = this.reason)
}
