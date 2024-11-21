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

package com.duckduckgo.site.permissions.store.sitepermissions

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType.ASK_EVERY_TIME

@Entity(tableName = "site_permissions")
data class SitePermissionsEntity(
    @PrimaryKey val domain: String,
    val askCameraSetting: String = ASK_EVERY_TIME.name,
    val askMicSetting: String = ASK_EVERY_TIME.name,
    val askDrmSetting: String = ASK_EVERY_TIME.name,
    val askLocationSetting: String = ASK_EVERY_TIME.name,
)

enum class SitePermissionAskSettingType {
    ASK_EVERY_TIME,
    DENY_ALWAYS,
    ALLOW_ALWAYS,
}
