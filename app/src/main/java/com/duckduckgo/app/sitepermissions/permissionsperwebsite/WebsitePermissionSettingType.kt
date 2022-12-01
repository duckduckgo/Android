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

package com.duckduckgo.app.sitepermissions.permissionsperwebsite

import com.duckduckgo.app.browser.R
import com.duckduckgo.app.sitepermissions.permissionsperwebsite.WebsitePermissionSettingType.ALLOW
import com.duckduckgo.app.sitepermissions.permissionsperwebsite.WebsitePermissionSettingType.ASK
import com.duckduckgo.app.sitepermissions.permissionsperwebsite.WebsitePermissionSettingType.ASK_DISABLED
import com.duckduckgo.app.sitepermissions.permissionsperwebsite.WebsitePermissionSettingType.DENY
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType
import java.io.Serializable

data class WebsitePermissionSetting(
    val icon: Int,
    val title: Int,
    val setting: WebsitePermissionSettingType,
) : Serializable {

    fun getOptionIndex(): Int {
        return when (this.setting) {
            ASK, ASK_DISABLED -> 1
            DENY -> 2
            ALLOW -> 3
        }
    }

    companion object {
        fun Int.getPermissionSettingOptionForIndex(): WebsitePermissionSettingType {
            return when (this) {
                2 -> DENY
                3 -> ALLOW
                else -> ASK
            }
        }
    }
}

enum class WebsitePermissionSettingType {
    ASK,
    ASK_DISABLED,
    ALLOW,
    DENY,
    ;

    fun toPrettyStringRes(): Int =
        when (this) {
            ASK -> R.string.permissionsPerWebsiteAskSetting
            ASK_DISABLED -> R.string.permissionsPerWebsiteAskDisabledSetting
            ALLOW -> R.string.permissionsPerWebsiteAllowSetting
            DENY -> R.string.permissionsPerWebsiteDenySetting
        }

    fun toSitePermissionSettingEntityType(): SitePermissionAskSettingType =
        when (this) {
            ASK, ASK_DISABLED -> SitePermissionAskSettingType.ASK_EVERY_TIME
            ALLOW -> SitePermissionAskSettingType.ALLOW_ALWAYS
            DENY -> SitePermissionAskSettingType.DENY_ALWAYS
        }

    companion object {
        fun mapToWebsitePermissionSetting(askSettingType: String?): WebsitePermissionSettingType =
            when (askSettingType) {
                SitePermissionAskSettingType.ALLOW_ALWAYS.name -> ALLOW
                SitePermissionAskSettingType.DENY_ALWAYS.name -> DENY
                else -> ASK
            }
    }
}
