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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DiffUtil.Callback
import androidx.recyclerview.widget.DiffUtil.DiffResult
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
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.AutoCompleteItem.Divider
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.AutoCompleteItem.Suggestion
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.AutoCompleteViewHolder.EmptySuggestionViewHolder
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.BOOKMARK_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.DEFAULT_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.DIVIDER_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.DUCK_AI_PROMPT_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.EMPTY_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.HISTORY_SEARCH_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.HISTORY_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.IN_APP_MESSAGE_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.SUGGESTION_TYPE
import com.duckduckgo.duckchat.impl.inputscreen.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.SWITCH_TO_TAB_TYPE

private sealed interface AutoCompleteItem {
    data class Suggestion(val value: AutoCompleteSuggestion) : AutoCompleteItem
    data object Divider : AutoCompleteItem
}

private val AutoCompleteSuggestion.isSearchItem: Boolean
    get() = this is AutoCompleteSearchSuggestion && !this.isAllowedInTopHits

class BrowserAutoCompleteSuggestionsAdapter(
    private val immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
    private val editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
    private val autoCompleteInAppMessageDismissedListener: () -> Unit,
    private val autoCompleteOpenSettingsClickListener: () -> Unit,
    private val autoCompleteLongPressClickListener: (AutoCompleteSuggestion) -> Unit,
    omnibarPosition: OmnibarPosition,
) : RecyclerView.Adapter<AutoCompleteViewHolder>() {

    private val deleteClickListener: (AutoCompleteSuggestion) -> Unit = {
        val suggestions = getSuggestions().filter { suggestion -> suggestion != it }
        val newItems = createItemsWithDividers(suggestions)
        val diffResult = calculateDiff(newItems)
        items = newItems
        // Animate the change
        diffResult.dispatchUpdatesTo(this)
        autoCompleteInAppMessageDismissedListener()
    }

    private fun calculateDiff(newItems: List<AutoCompleteItem>): DiffResult {
        return DiffUtil.calculateDiff(
            object : Callback() {
                override fun getOldListSize() = items.size
                override fun getNewListSize() = newItems.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    when {
                        items[oldItemPosition] is Divider && newItems[newItemPosition] is Divider -> true
                        items[oldItemPosition] is Suggestion && newItems[newItemPosition] is Suggestion ->
                            (items[oldItemPosition] as Suggestion).value == (newItems[newItemPosition] as Suggestion).value
                        else -> false
                    }
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = true
            },
        )
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
        DUCK_AI_PROMPT_TYPE to DuckAIPromptSuggestionViewHolderFactory(),
    )

    private var phrase = ""
    private var items: List<AutoCompleteItem> = emptyList()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): AutoCompleteViewHolder =
        viewHolderFactoryMap.getValue(viewType).onCreateViewHolder(parent)

    override fun getItemViewType(position: Int): Int = when (val item = items[position]) {
        is Divider -> DIVIDER_TYPE
        is Suggestion -> when (item.value) {
            is AutoCompleteBookmarkSuggestion -> BOOKMARK_TYPE
            is AutoCompleteHistorySuggestion -> HISTORY_TYPE
            is AutoCompleteHistorySearchSuggestion -> HISTORY_SEARCH_TYPE
            is AutoCompleteInAppMessageSuggestion -> IN_APP_MESSAGE_TYPE
            is AutoCompleteDefaultSuggestion -> DEFAULT_TYPE
            is AutoCompleteSwitchToTabSuggestion -> SWITCH_TO_TAB_TYPE
            is AutoCompleteUrlSuggestion -> SWITCH_TO_TAB_TYPE
            is AutoCompleteSuggestion.AutoCompleteDuckAIPrompt -> DUCK_AI_PROMPT_TYPE
            else -> SUGGESTION_TYPE
        }
    }

    override fun onBindViewHolder(holder: AutoCompleteViewHolder, position: Int) {
        if (holder is EmptySuggestionViewHolder) return

        when (val item = items[position]) {
            Divider -> return
            is Suggestion -> {
                viewHolderFactoryMap.getValue(getItemViewType(position))
                    .onBindViewHolder(
                        holder,
                        item.value,
                        immediateSearchClickListener,
                        editableSearchClickListener,
                        deleteClickListener,
                        autoCompleteOpenSettingsClickListener,
                        autoCompleteLongPressClickListener,
                    )
            }
        }
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
        if (phrase == newPhrase && getSuggestions() == newSuggestions) return
        phrase = newPhrase
        items = createItemsWithDividers(newSuggestions)
        notifyDataSetChanged()
    }

    private fun getSuggestions() = items
        .filterIsInstance<Suggestion>()
        .map { it.value }

    private fun createItemsWithDividers(suggestions: List<AutoCompleteSuggestion>): List<AutoCompleteItem> = buildList {
        suggestions.zipWithNext { current, next ->
            add(Suggestion(current))
            if (needsDivider(current, next)) add(Divider)
        }
        if (suggestions.isNotEmpty()) add(Suggestion(suggestions.last()))
    }

    private fun needsDivider(current: AutoCompleteSuggestion, next: AutoCompleteSuggestion): Boolean {
        return current.isSearchItem != next.isSearchItem
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
        const val DUCK_AI_PROMPT_TYPE = 10
    }
}
