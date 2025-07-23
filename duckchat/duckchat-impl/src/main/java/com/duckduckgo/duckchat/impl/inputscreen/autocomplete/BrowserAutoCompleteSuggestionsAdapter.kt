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

package com.duckduckgo.duckchat.impl.inputscreen.autocomplete

import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteDefaultSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteInAppMessageSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.AutoCompleteViewHolder.EmptySuggestionViewHolder
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.BOOKMARK_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.DEFAULT_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.DIVIDER_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.EMPTY_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.HISTORY_SEARCH_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.HISTORY_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.IN_APP_MESSAGE_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.SUGGESTION_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.SWITCH_TO_TAB_TYPE

// TODO: Should be moved to the API when we refactor the browser screen with the same logic.
private object AutoCompleteDivider

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
        DIVIDER_TYPE to DividerViewHolderFactory(),
    )

    private var phrase = ""
    private var suggestions: List<AutoCompleteSuggestion> = emptyList()
    private var items: List<Any> = emptyList()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): AutoCompleteViewHolder =
        viewHolderFactoryMap.getValue(viewType).onCreateViewHolder(parent)

    override fun getItemViewType(position: Int): Int {
        return when {
            items.isEmpty() -> EMPTY_TYPE
            items[position] is AutoCompleteBookmarkSuggestion -> BOOKMARK_TYPE
            items[position] is AutoCompleteHistorySuggestion -> HISTORY_TYPE
            items[position] is AutoCompleteHistorySearchSuggestion -> HISTORY_SEARCH_TYPE
            items[position] is AutoCompleteInAppMessageSuggestion -> IN_APP_MESSAGE_TYPE
            items[position] is AutoCompleteDefaultSuggestion -> DEFAULT_TYPE
            items[position] is AutoCompleteSwitchToTabSuggestion -> SWITCH_TO_TAB_TYPE
            items[position] is AutoCompleteUrlSuggestion -> SWITCH_TO_TAB_TYPE
            items[position] is AutoCompleteDivider -> DIVIDER_TYPE
            else -> SUGGESTION_TYPE
        }
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        position: Int,
    ) {
        if (holder is EmptySuggestionViewHolder) return

        val item = items[position]
        if (item is AutoCompleteDivider) return

        viewHolderFactoryMap.getValue(getItemViewType(position)).onBindViewHolder(
            holder,
            item as AutoCompleteSuggestion,
            immediateSearchClickListener,
            editableSearchClickListener,
            deleteClickListener,
            autoCompleteOpenSettingsClickListener,
            autoCompleteLongPressClickListener,
        )
    }

    override fun getItemCount(): Int {
        if (items.isEmpty() && phrase.isNotBlank()) {
            return 1 // Empty ViewHolder
        }
        return items.size
    }

    @UiThread
    fun updateData(
        newPhrase: String,
        newSuggestions: List<AutoCompleteSuggestion>,
    ) {
        if (phrase == newPhrase && suggestions == newSuggestions) return
        phrase = newPhrase
        suggestions = newSuggestions
        items = createItemsWithDividers(newSuggestions)
        notifyDataSetChanged()
    }

    private fun createItemsWithDividers(suggestions: List<AutoCompleteSuggestion>): List<Any> = buildList {
        suggestions.zipWithNext { current, next ->
            add(current)
            if (needsDivider(current, next)) add(AutoCompleteDivider)
        }
        if (suggestions.isNotEmpty()) add(suggestions.last())
    }

    private fun needsDivider(current: AutoCompleteSuggestion, next: AutoCompleteSuggestion): Boolean {
        val currentType = getItemType(current)
        val nextType = getItemType(next)

        return (currentType == SEARCH_ITEM && nextType == OTHER_ITEM) || (currentType == OTHER_ITEM && nextType == SEARCH_ITEM)
    }

    private fun getItemType(suggestion: AutoCompleteSuggestion): String {
        return when (suggestion) {
            is AutoCompleteSearchSuggestion -> if (suggestion.isAllowedInTopHits) OTHER_ITEM else SEARCH_ITEM
            else -> OTHER_ITEM
        }
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
        const val DIVIDER_TYPE = 9
    }

    companion object {
        const val SEARCH_ITEM = "SEARCH_ITEM"
        const val OTHER_ITEM = "OTHER_ITEM"
    }
}
