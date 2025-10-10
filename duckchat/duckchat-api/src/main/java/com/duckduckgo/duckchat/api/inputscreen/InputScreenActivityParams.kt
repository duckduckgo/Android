/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.api.inputscreen

import android.app.Activity
import androidx.activity.result.ActivityResult
import com.duckduckgo.navigation.api.GlobalActivityStarter

/**
 * Parameters for launching the Input Screen activity.
 *
 * @param query The initial query text to pre-populate in the input field
 * @param isTopOmnibar whether the omnibar is positioned at the top of the screen
 * @param tabs number of tabs to display
 */
data class InputScreenActivityParams(
    val query: String,
    val tabs: Int,
    val isTopOmnibar: Boolean = false,
) : GlobalActivityStarter.ActivityParams

/**
 * Result codes returned by the Input Screen activity's [ActivityResult].
 */
data object InputScreenActivityResultCodes {
    /** User requested to perform a new search */
    const val NEW_SEARCH_REQUESTED = 1

    /** User requested to switch to an existing tab */
    const val SWITCH_TO_TAB_REQUESTED = 2

    /** User requested to launch the Fire Button */
    const val FIRE_BUTTON_REQUESTED = 3

    /** User requested to launch the Tab Switcher */
    const val TAB_SWITCHER_REQUESTED = 4

    /** User requested to launch the Browser Menu */
    const val MENU_REQUESTED = 5
}

/**
 * Parameter names for data returned by the Input Screen activity's [ActivityResult.getData].
 */
data object InputScreenActivityResultParams {
    /** Key for the search query string when result is [InputScreenActivityResultCodes.NEW_SEARCH_REQUESTED] */
    const val SEARCH_QUERY_PARAM = "query"

    /** Key for the target tab ID when result is [InputScreenActivityResultCodes.SWITCH_TO_TAB_REQUESTED] */
    const val TAB_ID_PARAM = "tab_id"

    /** Key for any canceled draft content when result is [Activity.RESULT_CANCELED] */
    const val CANCELED_DRAFT_PARAM = "draft"
}
