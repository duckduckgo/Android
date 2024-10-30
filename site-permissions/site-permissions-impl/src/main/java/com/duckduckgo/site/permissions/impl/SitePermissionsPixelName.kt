/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.site.permissions.impl

import com.duckduckgo.app.statistics.pixels.Pixel

internal enum class SitePermissionsPixelName(override val pixelName: String) : Pixel.PixelName {
    PRECISE_LOCATION_SYSTEM_DIALOG_ENABLE("m_pc_syd_e"),
    PRECISE_LOCATION_SYSTEM_DIALOG_LATER("m_pc_syd_l"),
    PRECISE_LOCATION_SYSTEM_DIALOG_NEVER("m_pc_syd_n"),
    PRECISE_LOCATION_SETTINGS_LOCATION_PERMISSION_ENABLE("m_pc_s_l_e"),
    PRECISE_LOCATION_SETTINGS_LOCATION_PERMISSION_DISABLE("m_pc_s_l_d"),
    PRECISE_LOCATION_SITE_DIALOG_ALLOW_ALWAYS("m_pc_sd_aa"),
    PRECISE_LOCATION_SITE_DIALOG_ALLOW_ONCE("m_pc_sd_ao"),
    PRECISE_LOCATION_SITE_DIALOG_DENY_ALWAYS("m_pc_sd_da"),
    PRECISE_LOCATION_SITE_DIALOG_DENY_ONCE("m_pc_sd_do"),
}
