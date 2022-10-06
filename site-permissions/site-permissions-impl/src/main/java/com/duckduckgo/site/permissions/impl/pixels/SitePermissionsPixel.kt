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

package com.duckduckgo.site.permissions.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

class SitePermissionsPixel {

    enum class SitePermissionsPixelName(override val pixelName: String) : Pixel.PixelName {
        SITE_PERMISSION_DIALOG_SHOWN("m_site_permission_dialog_shown"),
        SITE_PERMISSION_DIALOG_ALLOWED("m_site_permission_dialog_allowed"),
        SITE_PERMISSION_DIALOG_DENIED("m_site_permission_dialog_denied"),
        SITE_PERMISSIONS_SETTINGS_VISITED("m_site_permissions_settings_visited"),
        SITE_PERMISSIONS_ASK_DISABLED("m_site_permission_ask_disabled"),
        SITE_PERMISSIONS_ASK_ENABLED("m_site_permission_ask_enabled"),
        SITE_PERMISSIONS_SETTING_CHANGED("m_website_permissions_setting_changed")
    }

    object PixelParameter {
        const val VALUE = "value"
        const val SITE_PERMISSION = "site_permission"
    }

    object PixelValue {
        const val CAMERA = "camera"
        const val MIC = "mic"
        const val BOTH = "both"
        const val LOCATION = "location"
        const val ALLOW = "allow"
        const val DENY = "deny"
        const val ASK = "ask"
    }
}
