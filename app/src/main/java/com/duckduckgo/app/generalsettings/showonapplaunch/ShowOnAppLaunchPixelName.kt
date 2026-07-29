/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.generalsettings.showonapplaunch

import com.duckduckgo.app.statistics.pixels.Pixel

/** Pixels fired from the After Inactivity (show-on-app-launch) settings screen. */
enum class ShowOnAppLaunchPixelName(override val pixelName: String) : Pixel.PixelName {
    // Return To Last Tab settings toggle pixels
    LAST_TAB_SHORTCUT_SETTING_ENABLED("m_ntp_after_idle_last_tab_shortcut_setting_enabled_count"),
    LAST_TAB_SHORTCUT_SETTING_ENABLED_DAILY("m_ntp_after_idle_last_tab_shortcut_setting_enabled_daily"),
    LAST_TAB_SHORTCUT_SETTING_DISABLED("m_ntp_after_idle_last_tab_shortcut_setting_disabled_count"),
    LAST_TAB_SHORTCUT_SETTING_DISABLED_DAILY("m_ntp_after_idle_last_tab_shortcut_setting_disabled_daily"),

    // After-inactivity launch option selected pixels (New Tab Page / Last Opened Tab / Specific Page)
    LAUNCH_OPTION_NEW_TAB_PAGE("m_ntp_after_idle_launch_option_new_tab_page_count"),
    LAUNCH_OPTION_NEW_TAB_PAGE_DAILY("m_ntp_after_idle_launch_option_new_tab_page_daily"),
    LAUNCH_OPTION_LAST_OPENED_TAB("m_ntp_after_idle_launch_option_last_opened_tab_count"),
    LAUNCH_OPTION_LAST_OPENED_TAB_DAILY("m_ntp_after_idle_launch_option_last_opened_tab_daily"),
    LAUNCH_OPTION_SPECIFIC_PAGE("m_ntp_after_idle_launch_option_specific_page_count"),
    LAUNCH_OPTION_SPECIFIC_PAGE_DAILY("m_ntp_after_idle_launch_option_specific_page_daily"),
}
