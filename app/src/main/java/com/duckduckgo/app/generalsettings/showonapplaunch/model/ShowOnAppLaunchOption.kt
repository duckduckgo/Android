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

package com.duckduckgo.app.generalsettings.showonapplaunch.model

import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_GENERAL_APP_LAUNCH_LAST_OPENED_TAB_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_GENERAL_APP_LAUNCH_NEW_TAB_PAGE_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_GENERAL_APP_LAUNCH_SPECIFIC_PAGE_SELECTED

sealed class ShowOnAppLaunchOption(val id: Int) {

    data object LastOpenedTab : ShowOnAppLaunchOption(1)
    data object NewTabPage : ShowOnAppLaunchOption(2)
    data class SpecificPage(val url: String, val resolvedUrl: String? = null) : ShowOnAppLaunchOption(3)

    companion object {

        fun mapToOption(id: Int): ShowOnAppLaunchOption = when (id) {
            1 -> LastOpenedTab
            2 -> NewTabPage
            3 -> SpecificPage("")
            else -> throw IllegalArgumentException("Unknown id: $id")
        }

        fun getPixelName(option: ShowOnAppLaunchOption) = when (option) {
            LastOpenedTab -> SETTINGS_GENERAL_APP_LAUNCH_LAST_OPENED_TAB_SELECTED
            NewTabPage -> SETTINGS_GENERAL_APP_LAUNCH_NEW_TAB_PAGE_SELECTED
            is SpecificPage -> SETTINGS_GENERAL_APP_LAUNCH_SPECIFIC_PAGE_SELECTED
        }

        fun getDailyPixelValue(option: ShowOnAppLaunchOption) = when (option) {
            LastOpenedTab -> "last_opened_tab"
            NewTabPage -> "new_tab_page"
            is SpecificPage -> "specific_page"
        }
    }
}
