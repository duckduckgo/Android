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
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.browser.autocomplete.AutoCompleteViewHolder.EmptySuggestionViewHolder
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.BOOKMARK_TYPE
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.EMPTY_TYPE
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.Type.SUGGESTION_TYPE

class BrowserAutoCompleteSuggestionsAdapter(
    private val immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
    private val editableSearchClickListener: (AutoCompleteSuggestion) -> Unit
) : RecyclerView.Adapter<AutoCompleteViewHolder>() {

    private val viewHolderFactoryMap: Map<Int, SuggestionViewHolderFactory> = mapOf(
        EMPTY_TYPE to EmptySuggestionViewHolderFactory(),
        SUGGESTION_TYPE to SearchSuggestionViewHolderFactory(),
        BOOKMARK_TYPE to BookmarkSuggestionViewHolderFactory()
    )

    private var phrase = ""
    private var suggestions: List<AutoCompleteSuggestion> = emptyList()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AutoCompleteViewHolder =
        viewHolderFactoryMap.getValue(viewType).onCreateViewHolder(parent)

    override fun getItemViewType(position: Int): Int {
        return when {
            suggestions.isEmpty() -> EMPTY_TYPE
            suggestions[position] is AutoCompleteBookmarkSuggestion -> BOOKMARK_TYPE
            else -> SUGGESTION_TYPE
        }
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        position: Int
    ) {
        if (holder is EmptySuggestionViewHolder) {
            // nothing required
        } else {
            viewHolderFactoryMap.getValue(getItemViewType(position)).onBindViewHolder(
                holder,
                suggestions[position],
                immediateSearchClickListener,
                editableSearchClickListener
            )
        }
    }

    override fun getItemCount(): Int {
        if (suggestions.isEmpty() && phrase.isNotBlank()) {
            return 1 // No suggestions message
        }
        return suggestions.size
    }

    @UiThread
    fun updateData(
        newPhrase: String,
        newSuggestions: List<AutoCompleteSuggestion>
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
    }
}
