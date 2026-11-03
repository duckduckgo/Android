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

/**
 * A null setting means the user never chose one for this site; ASK_EVERY_TIME is an explicit choice.
 */
@Entity(tableName = "site_permissions")
data class SitePermissionsEntity(
    @PrimaryKey val domain: String,
    val askCameraSetting: String? = null,
    val askMicSetting: String? = null,
    val askDrmSetting: String? = null,
    val askLocationSetting: String? = null,
)

enum class SitePermissionAskSettingType {
    ASK_EVERY_TIME,
    DENY_ALWAYS,
    ALLOW_ALWAYS,
}
