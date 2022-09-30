// ktlint-disable filename
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
package com.duckduckgo.cookies.store

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.duckduckgo.cookies.api.TrackingCookie1pException

@Entity(tableName = "first_party_cookie_tracker_policy")
data class FirstPartyTrackerCookiePolicyEntity(
    @PrimaryKey val id: Int = 1,
    val threshold: Int,
    val maxAge: Int,
)

@Entity(tableName = "tracking_cookies_1p_exceptions")
data class TrackingCookie1pExceptionEntity(
    @PrimaryKey val domain: String,
    val reason: String
)

fun TrackingCookie1pExceptionEntity.toTrackingCookie1p(): TrackingCookie1pException {
    return TrackingCookie1pException(domain = this.domain, reason = this.reason)
}
