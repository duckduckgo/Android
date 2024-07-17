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
    FAVOURITES_LIST_EXPANDED("m_new_tab_page_favorites_expanded"),
    FAVOURITES_LIST_COLLAPSED("m_new_tab_page_favorites_collapsed"),
    FAVOURITES_TOOLTIP_PRESSED("m_new_tab_page_favorites_info_tooltip"),
    CUSTOMIZE_PAGE_PRESSED("m_new_tab_page_customize"),
    SHORTCUT_PRESSED("m_new_tab_page_shortcut_clicked_"),
    SHORTCUT_REMOVED("m_new_tab_page_customize_shortcut_removed_"),
    SHORTCUT_ADDED("m_new_tab_page_customize_shortcut_added_"),
    SHORTCUT_SECTION_TOGGLED_OFF("m_new_tab_page_customize_section_off_shortcuts"),
    SHORTCUT_SECTION_TOGGLED_ON("m_new_tab_page_customize_section_on_shortcuts"),
    SECTION_REARRANGED("m_new_tab_page_customize_section_reordered"),
    NEW_TAB_DISPLAYED("m_new_tab_page_displayed"),
    NEW_TAB_DISPLAYED_UNIQUE("m_new_tab_page_displayed_unique"),
}
