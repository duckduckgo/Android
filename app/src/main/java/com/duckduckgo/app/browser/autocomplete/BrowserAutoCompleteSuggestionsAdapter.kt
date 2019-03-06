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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi.AutoCompleteSuggestion
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.AutoCompleteViewHolder
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.AutoCompleteViewHolder.EmptySuggestionViewHolder
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter.AutoCompleteViewHolder.SuggestionViewHolder
import kotlinx.android.synthetic.main.item_autocomplete_suggestion.view.*
import javax.inject.Inject

class BrowserAutoCompleteSuggestionsAdapter @Inject constructor(
    private val immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
    private val editableSearchClickListener: (AutoCompleteSuggestion) -> Unit
) : RecyclerView.Adapter<AutoCompleteViewHolder>() {

    private val suggestions: MutableList<AutoCompleteSuggestion> = ArrayList()


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AutoCompleteViewHolder {

        val inflater = LayoutInflater.from(parent.context)

        return if (suggestions.isEmpty()) {
            EmptySuggestionViewHolder(inflater.inflate(R.layout.item_autocomplete_no_suggestions, parent, false))
        } else {
            SuggestionViewHolder(inflater.inflate(R.layout.item_autocomplete_suggestion, parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (suggestions.isEmpty()) {
            return EMPTY_TYPE
        }

        return SUGGESTION_TYPE
    }

    override fun onBindViewHolder(holder: AutoCompleteViewHolder, position: Int) {
        when (holder) {
            is EmptySuggestionViewHolder -> {
                // nothing required
            }
            is SuggestionViewHolder -> {
                val suggestion = suggestions[position]
                holder.bind(suggestion, immediateSearchClickListener, editableSearchClickListener)
            }
        }
    }

    override fun getItemCount(): Int {
        if (suggestions.isNotEmpty()) {
            return suggestions.size
        }

        // if there are no suggestions, we'll use a recycler row to display "no suggestions"
        return 1
    }

    @UiThread
    fun updateData(newSuggestions: List<AutoCompleteSuggestion>) {
        suggestions.clear()
        suggestions.addAll(newSuggestions)
        notifyDataSetChanged()
    }

    sealed class AutoCompleteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        class SuggestionViewHolder(itemView: View) : AutoCompleteViewHolder(itemView) {
            fun bind(
                item: AutoCompleteSuggestion,
                immediateSearchListener: (AutoCompleteSuggestion) -> Unit,
                editableSearchClickListener: (AutoCompleteSuggestion) -> Unit
            ) = with(itemView) {

                phrase.text = item.phrase

                val phraseOrUrlImage = if (item.isUrl) R.drawable.ic_globe_24dp else R.drawable.ic_loupe_24dp
                phraseOrUrlIndicator.setImageResource(phraseOrUrlImage)

                editQueryImage.setOnClickListener { editableSearchClickListener(item) }
                setOnClickListener { immediateSearchListener(item) }
            }
        }

        class EmptySuggestionViewHolder(itemView: View) : AutoCompleteViewHolder(itemView)
    }

    companion object {
        private const val EMPTY_TYPE = 1
        private const val SUGGESTION_TYPE = 2
    }
}

