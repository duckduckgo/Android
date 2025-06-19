/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.browser.autocomplete

import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.autocomplete.AutoCompleteViewHolder.EmptySuggestionViewHolder
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.BOOKMARK_TYPE
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.DEFAULT_TYPE
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.EMPTY_TYPE
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.HISTORY_SEARCH_TYPE
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.HISTORY_TYPE
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.IN_APP_MESSAGE_TYPE
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.SUGGESTION_TYPE
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.SWITCH_TO_TAB_TYPE
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteDefaultSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteInAppMessageSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion

class BrowserAutoCompleteSuggestionsAdapter(
    private val immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
    private val editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
    private val autoCompleteInAppMessageDismissedListener: () -> Unit,
    private val autoCompleteOpenSettingsClickListener: () -> Unit,
    private val autoCompleteLongPressClickListener: (AutoCompleteSuggestion) -> Unit,
    omnibarPosition: OmnibarPosition,
) : RecyclerView.Adapter<AutoCompleteViewHolder>() {

    private val deleteClickListener: (AutoCompleteSuggestion) -> Unit = {
        suggestions = suggestions.filter { suggestion -> suggestion != it }
        notifyItemRemoved((suggestions.indexOf(it)))
        autoCompleteInAppMessageDismissedListener()
    }

    private val viewHolderFactoryMap: Map<Int, SuggestionViewHolderFactory> = mapOf(
        EMPTY_TYPE to EmptySuggestionViewHolderFactory(),
        SUGGESTION_TYPE to SearchSuggestionViewHolderFactory(omnibarPosition),
        BOOKMARK_TYPE to BookmarkSuggestionViewHolderFactory(),
        HISTORY_TYPE to HistorySuggestionViewHolderFactory(),
        HISTORY_SEARCH_TYPE to HistorySearchSuggestionViewHolderFactory(),
        IN_APP_MESSAGE_TYPE to InAppMessageViewHolderFactory(),
        DEFAULT_TYPE to DefaultSuggestionViewHolderFactory(omnibarPosition),
        SWITCH_TO_TAB_TYPE to SwitchToTabSuggestionViewHolderFactory(),
    )

    private var phrase = ""
    private var suggestions: List<AutoCompleteSuggestion> = emptyList()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): AutoCompleteViewHolder =
        viewHolderFactoryMap.getValue(viewType).onCreateViewHolder(parent)

    override fun getItemViewType(position: Int): Int {
        return when {
            suggestions.isEmpty() -> EMPTY_TYPE
            suggestions[position] is AutoCompleteBookmarkSuggestion -> BOOKMARK_TYPE
            suggestions[position] is AutoCompleteHistorySuggestion -> HISTORY_TYPE
            suggestions[position] is AutoCompleteHistorySearchSuggestion -> HISTORY_SEARCH_TYPE
            suggestions[position] is AutoCompleteInAppMessageSuggestion -> IN_APP_MESSAGE_TYPE
            suggestions[position] is AutoCompleteDefaultSuggestion -> DEFAULT_TYPE
            suggestions[position] is AutoCompleteSwitchToTabSuggestion -> SWITCH_TO_TAB_TYPE
            suggestions[position] is AutoCompleteUrlSuggestion -> SWITCH_TO_TAB_TYPE
            else -> SUGGESTION_TYPE
        }
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        position: Int,
    ) {
        if (holder is EmptySuggestionViewHolder) {
            // nothing required
        } else {
            viewHolderFactoryMap.getValue(getItemViewType(position)).onBindViewHolder(
                holder,
                suggestions[position],
                immediateSearchClickListener,
                editableSearchClickListener,
                deleteClickListener,
                autoCompleteOpenSettingsClickListener,
                autoCompleteLongPressClickListener,
            )
        }
    }

    override fun getItemCount(): Int {
        if (suggestions.isEmpty() && phrase.isNotBlank()) {
            return 1 // Empty ViewHolder
        }
        return suggestions.size
    }

    @UiThread
    fun updateData(
        newPhrase: String,
        newSuggestions: List<AutoCompleteSuggestion>,
    ) {
        if (phrase == newPhrase && suggestions == newSuggestions) return
        phrase = newPhrase
        suggestions = newSuggestions
        notifyDataSetChanged()
    }

    object Type {
        const val EMPTY_TYPE = 1
        const val SUGGESTION_TYPE = 2
        const val BOOKMARK_TYPE = 3
        const val HISTORY_TYPE = 4
        const val HISTORY_SEARCH_TYPE = 5
        const val IN_APP_MESSAGE_TYPE = 6
        const val DEFAULT_TYPE = 7
        const val SWITCH_TO_TAB_TYPE = 8
    }
}
