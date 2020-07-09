/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.location.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locationPermissions")
data class LocationPermissionEntity(
    @PrimaryKey val domain: String,
    val permission: LocationPermissionType
)

private const val WWW_HOST_PREFIX = "https://"
private const val WWW_SUFFIX = "/"

fun LocationPermissionEntity.host(): String {
    return domain.takeIf { it.startsWith(WWW_HOST_PREFIX, ignoreCase = true) && it.endsWith(WWW_SUFFIX, ignoreCase = true) }
        ?.drop(WWW_HOST_PREFIX.length)?.dropLast(WWW_SUFFIX.length) ?: domain
}


private const val TYPE_ALLOW_ALWAYS = 1
private const val TYPE_ALLOW_ONCE = 2
private const val TYPE_DENY_ALWAYS = 3
private const val TYPE_DENY_ONCE = 4

enum class LocationPermissionType(val value: Int) {

    ALLOW_ALWAYS(TYPE_ALLOW_ALWAYS),
    ALLOW_ONCE(TYPE_ALLOW_ONCE),
    DENY_ALWAYS(TYPE_DENY_ALWAYS),
    DENY_ONCE(TYPE_DENY_ONCE);

    companion object {
        private val map = values().associateBy(LocationPermissionType::value)
        fun fromValue(value: Int) = map[value]
    }

}
