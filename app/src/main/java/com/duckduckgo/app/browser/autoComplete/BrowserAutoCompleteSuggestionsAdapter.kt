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

package com.duckduckgo.app.privacymonitor.ui

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi.AutoCompleteSuggestion
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacymonitor.ui.BrowserAutoCompleteSuggestionsAdapter.SuggestionViewHolder
import kotlinx.android.synthetic.main.item_autocomplete_suggestion.view.*
import timber.log.Timber
import javax.inject.Inject

class BrowserAutoCompleteSuggestionsAdapter @Inject constructor() : RecyclerView.Adapter<SuggestionViewHolder>() {

    private val suggestions: MutableList<AutoCompleteSuggestion> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val root = LayoutInflater.from(parent.context).inflate(R.layout.item_autocomplete_suggestion, parent, false)
        return SuggestionViewHolder(root)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val suggestion = suggestions[position]
        holder.bind(suggestion)
    }

    override fun getItemCount(): Int {
        return suggestions.size
    }

    fun updateData(newSuggestions: List<AutoCompleteSuggestion>) {

        Timber.i("Updating autosuggestions recycler with ${newSuggestions.size} items")

        suggestions.clear()
        suggestions.addAll(newSuggestions)
        notifyDataSetChanged()
    }

    class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(item: AutoCompleteSuggestion) = with(itemView) {
            phrase.text = item.phrase

            val phraseOrUrlImage = if (item.isUrl) R.drawable.dashboard_https_good else R.drawable.dashboard_https_bad
            phraseOrUrlIndicator.setImageResource(phraseOrUrlImage)
        }
    }

}

