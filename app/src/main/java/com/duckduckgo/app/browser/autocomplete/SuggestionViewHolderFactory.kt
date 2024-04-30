/*
 * Copyright (c) 2019 DuckDuckGo
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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ItemAutocompleteBookmarkSuggestionBinding
import com.duckduckgo.app.browser.databinding.ItemAutocompleteHistorySuggestionBinding
import com.duckduckgo.app.browser.databinding.ItemAutocompleteNoSuggestionsBinding
import com.duckduckgo.app.browser.databinding.ItemAutocompleteSearchSuggestionBinding

interface SuggestionViewHolderFactory {

    fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder

    fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
    )
}

class SearchSuggestionViewHolderFactory : SuggestionViewHolderFactory {

    override fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAutocompleteSearchSuggestionBinding.inflate(inflater, parent, false)
        return AutoCompleteViewHolder.SearchSuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        val searchSuggestionViewHolder = holder as AutoCompleteViewHolder.SearchSuggestionViewHolder
        searchSuggestionViewHolder.bind(suggestion as AutoCompleteSearchSuggestion, immediateSearchClickListener, editableSearchClickListener)
    }
}

class HistorySuggestionViewHolderFactory : SuggestionViewHolderFactory {

    override fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAutocompleteHistorySuggestionBinding.inflate(inflater, parent, false)
        return AutoCompleteViewHolder.HistorySuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        val searchSuggestionViewHolder = holder as AutoCompleteViewHolder.HistorySuggestionViewHolder
        searchSuggestionViewHolder.bind(suggestion as AutoCompleteHistorySuggestion, immediateSearchClickListener, editableSearchClickListener)
    }
}

class HistorySearchSuggestionViewHolderFactory : SuggestionViewHolderFactory {

    override fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAutocompleteSearchSuggestionBinding.inflate(inflater, parent, false)
        return AutoCompleteViewHolder.HistorySearchSuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        val historySearchSuggestionViewHolder = holder as AutoCompleteViewHolder.HistorySearchSuggestionViewHolder
        historySearchSuggestionViewHolder.bind(
            suggestion as AutoCompleteHistorySearchSuggestion,
            immediateSearchClickListener,
            editableSearchClickListener,
        )
    }
}

class BookmarkSuggestionViewHolderFactory : SuggestionViewHolderFactory {

    override fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAutocompleteBookmarkSuggestionBinding.inflate(inflater, parent, false)
        return AutoCompleteViewHolder.BookmarkSuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        val bookmarkSuggestionViewHolder = holder as AutoCompleteViewHolder.BookmarkSuggestionViewHolder
        bookmarkSuggestionViewHolder.bind(suggestion as AutoCompleteBookmarkSuggestion, immediateSearchClickListener, editableSearchClickListener)
    }
}

class EmptySuggestionViewHolderFactory : SuggestionViewHolderFactory {

    override fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return AutoCompleteViewHolder.EmptySuggestionViewHolder(ItemAutocompleteNoSuggestionsBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        // do nothing
    }
}

sealed class AutoCompleteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    class SearchSuggestionViewHolder(val binding: ItemAutocompleteSearchSuggestionBinding) : AutoCompleteViewHolder(binding.root) {
        fun bind(
            item: AutoCompleteSearchSuggestion,
            immediateSearchListener: (AutoCompleteSuggestion) -> Unit,
            editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        ) = with(binding) {
            phrase.text = item.phrase

            val phraseOrUrlImage = if (item.isUrl) R.drawable.ic_globe_20 else R.drawable.ic_find_search_20
            phraseOrUrlIndicator.setImageResource(phraseOrUrlImage)

            editQueryImage.setOnClickListener { editableSearchClickListener(item) }
            root.setOnClickListener { immediateSearchListener(item) }
        }
    }

    class HistorySearchSuggestionViewHolder(val binding: ItemAutocompleteSearchSuggestionBinding) : AutoCompleteViewHolder(binding.root) {
        fun bind(
            item: AutoCompleteHistorySearchSuggestion,
            immediateSearchListener: (AutoCompleteSuggestion) -> Unit,
            editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        ) = with(binding) {
            phrase.text = item.phrase

            phraseOrUrlIndicator.setImageResource(R.drawable.ic_history)

            editQueryImage.setOnClickListener { editableSearchClickListener(item) }
            root.setOnClickListener { immediateSearchListener(item) }
        }
    }

    class BookmarkSuggestionViewHolder(val binding: ItemAutocompleteBookmarkSuggestionBinding) : AutoCompleteViewHolder(binding.root) {
        fun bind(
            item: AutoCompleteBookmarkSuggestion,
            immediateSearchListener: (AutoCompleteSuggestion) -> Unit,
            editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        ) = with(binding) {
            title.text = item.title
            url.text = item.url

            bookmarkIndicator.setImageResource(if (item.isFavorite) R.drawable.ic_bookmark_favorite_20 else R.drawable.ic_bookmark_20)
            goToBookmarkImage.setOnClickListener { editableSearchClickListener(item) }
            root.setOnClickListener { immediateSearchListener(item) }
        }
    }

    class HistorySuggestionViewHolder(val binding: ItemAutocompleteHistorySuggestionBinding) : AutoCompleteViewHolder(binding.root) {
        fun bind(
            item: AutoCompleteHistorySuggestion,
            immediateSearchListener: (AutoCompleteSuggestion) -> Unit,
            editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        ) = with(binding) {
            title.text = item.title
            url.text = item.phrase

            goToSuggestionImage.setOnClickListener { editableSearchClickListener(item) }
            root.setOnClickListener { immediateSearchListener(item) }
        }
    }

    class EmptySuggestionViewHolder(binding: ItemAutocompleteNoSuggestionsBinding) : AutoCompleteViewHolder(binding.root)
}
