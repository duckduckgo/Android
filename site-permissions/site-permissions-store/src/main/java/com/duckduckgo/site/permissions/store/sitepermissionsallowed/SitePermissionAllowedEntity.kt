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

package com.duckduckgo.site.permissions.store.sitepermissionsallowed

import androidx.room.Entity
import kotlin.math.abs

@Entity(
    tableName = "site_permission_allowed",
    primaryKeys = ["domain", "tabId", "permissionAllowed"],
)
data class SitePermissionAllowedEntity(
    val domain: String,
    val tabId: String,
    val permissionAllowed: String,
    val allowedAt: Long,
) {

    fun allowedWithin24h(): Boolean {
        val now = System.currentTimeMillis()
        val diff = abs(now - this.allowedAt) / 3600000
        return diff <= 24
    }
}
