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

package com.duckduckgo.newtabpage.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

enum class NewTabPixelNames(override val pixelName: String) : Pixel.PixelName {
    CUSTOMIZE_PAGE_PRESSED("m_new_tab_page_customize"),
    SHORTCUT_PRESSED("m_new_tab_page_shortcut_clicked_"),
    SHORTCUT_REMOVED("m_new_tab_page_customize_shortcut_removed_"),
    SHORTCUT_ADDED("m_new_tab_page_customize_shortcut_added_"),
    SHORTCUT_SECTION_TOGGLED_OFF("m_new_tab_page_customize_section_off_shortcuts"),
    SHORTCUT_SECTION_TOGGLED_ON("m_new_tab_page_customize_section_on_shortcuts"),
    SECTION_REARRANGED("m_new_tab_page_customize_section_reordered"),
    NEW_TAB_DISPLAYED("m_new_tab_page_displayed"),
    NEW_TAB_DISPLAYED_UNIQUE("m_new_tab_page_displayed_unique"),
    PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED("m_product_telemetry_surface_usage_new_tab_page"),
    PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED_DAILY("m_product_telemetry_surface_usage_new_tab_page_daily"),
}

object NewTabPixelParameters {
    const val SHORTCUTS = "shortcuts"
    const val FAVORITES = "favorites"
    const val APP_TRACKING_PROTECTION = "appTP"
    const val FAVORITES_COUNT = "favoriteCount"
}

object NewTabPixelValues {
    const val SECTION_ENABLED = "1"
    const val SECTION_DISABLED = "0"
    const val FAVORITES_2_3 = "2_3"
    const val FAVORITES_4_5 = "4_5"
    const val FAVORITES_6_10 = "6_10"
    const val FAVORITES_11_15 = "11_15"
    const val FAVORITES_16_25 = "16_25"
    const val FAVORITES_25 = ">25"
}
