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

package com.duckduckgo.app.autocomplete.impl

import com.duckduckgo.app.statistics.pixels.Pixel

enum class AutoCompletePixelNames(override val pixelName: String) : Pixel.PixelName {

    AUTOCOMPLETE_BOOKMARK_SELECTION("m_autocomplete_click_bookmark"),
    AUTOCOMPLETE_FAVORITE_SELECTION("m_autocomplete_click_favorite"),
    AUTOCOMPLETE_SEARCH_PHRASE_SELECTION("m_autocomplete_click_phrase"),
    AUTOCOMPLETE_SEARCH_WEBSITE_SELECTION("m_autocomplete_click_website"),

    AUTOCOMPLETE_HISTORY_SEARCH_SELECTION("m_autocomplete_click_history_search"),
    AUTOCOMPLETE_HISTORY_SITE_SELECTION("m_autocomplete_click_history_site"),

    AUTOCOMPLETE_SWITCH_TO_TAB_SELECTION("m_autocomplete_click_switch_to_tab"),

    AUTOCOMPLETE_DUCKAI_PROMPT_EXPERIMENTAL_SELECTION("m_autocomplete_click_duckai_experimental"),
    AUTOCOMPLETE_DUCKAI_PROMPT_LEGACY_SELECTION("m_autocomplete_click_duckai_legacy"),
}

object AutocompletePixelParams {
    /**
     * Parameter to capture the index of the selected suggestion within the list of search suggestions
     * (either [AutoCompletePixelNames.AUTOCOMPLETE_SEARCH_PHRASE_SELECTION] or [AutoCompletePixelNames.AUTOCOMPLETE_SEARCH_WEBSITE_SELECTION]).
     */
    const val PARAM_SEARCH_SUGGESTION_INDEX = "search_suggestion_index"
}
