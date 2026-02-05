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

package com.duckduckgo.app.browser.customtabs

import com.duckduckgo.app.statistics.pixels.Pixel

enum class CustomTabPixelNames(override val pixelName: String) : Pixel.PixelName {
    CUSTOM_TABS_OPENED("m_custom_tabs_opened"),
    CUSTOM_TABS_PRIVACY_DASHBOARD_OPENED("m_custom_tabs_privacy_dashboard_opened"),
    CUSTOM_TABS_MENU_OPENED("m_custom_tabs_menu_opened"),
    CUSTOM_TABS_MENU_REFRESH("m_custom_tabs_menu_refresh"),
    CUSTOM_TABS_OPEN_IN_DDG("m_custom_tabs_open_in_ddg"),
    CUSTOM_TABS_MENU_DISABLE_PROTECTIONS_ALLOW_LIST_ADD("m_custom_tabs_menu_disable_protections_allow_list_add"),
    CUSTOM_TABS_MENU_DISABLE_PROTECTIONS_ALLOW_LIST_REMOVE("m_custom_tabs_menu_disable_protections_allow_list_remove"),
    CUSTOM_TABS_ADDRESS_BAR_CLICKED("m_custom_tabs_address_bar_clicked"),
    CUSTOM_TABS_ADDRESS_BAR_CLICKED_DAILY("m_custom_tabs_address_bar_clicked_daily"),
    CUSTOM_TABS_DAX_CLICKED("m_custom_tabs_dax_clicked"),
    CUSTOM_TABS_DAX_CLICKED_DAILY("m_custom_tabs_dax_clicked_daily"),
}
