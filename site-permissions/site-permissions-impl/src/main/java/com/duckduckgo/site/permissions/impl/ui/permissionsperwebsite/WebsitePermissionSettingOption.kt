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

package com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite

import androidx.annotation.StringRes
import com.duckduckgo.site.permissions.impl.R
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType
import java.io.Serializable

data class WebsitePermissionSetting(
    val icon: Int,
    val title: Int,
    val setting: WebsitePermissionSettingOption,
) : Serializable

enum class WebsitePermissionSettingOption(
    val order: Int,
    @StringRes val stringRes: Int,
) {
    ASK(1, R.string.permissionsPerWebsiteAskSetting),
    ASK_DISABLED(1, R.string.permissionsPerWebsiteAskDisabledSetting),
    DENY(2, R.string.permissionsPerWebsiteDenySetting),
    ALLOW(3, R.string.permissionsPerWebsiteAllowSetting),
    ;

    fun toSitePermissionSettingEntityType(): SitePermissionAskSettingType =
        when (this) {
            ASK, ASK_DISABLED -> SitePermissionAskSettingType.ASK_EVERY_TIME
            ALLOW -> SitePermissionAskSettingType.ALLOW_ALWAYS
            DENY -> SitePermissionAskSettingType.DENY_ALWAYS
        }

    companion object {
        fun mapToWebsitePermissionSetting(askSettingType: String?): WebsitePermissionSettingOption =
            when (askSettingType) {
                SitePermissionAskSettingType.ALLOW_ALWAYS.name -> ALLOW
                SitePermissionAskSettingType.DENY_ALWAYS.name -> DENY
                else -> ASK
            }

        fun Int.getPermissionSettingOptionFromPosition(): WebsitePermissionSettingOption {
            return entries.first { it.order == this }
        }
    }
}
