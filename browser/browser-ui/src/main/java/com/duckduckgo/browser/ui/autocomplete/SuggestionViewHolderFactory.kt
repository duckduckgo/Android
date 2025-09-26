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

package com.duckduckgo.browser.ui.autocomplete

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteDefaultSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion
import com.duckduckgo.browser.ui.R
import com.duckduckgo.browser.ui.autocomplete.AutoCompleteViewHolder.InAppMessageViewHolder
import com.duckduckgo.browser.ui.databinding.ItemAutocompleteBookmarkSuggestionBinding
import com.duckduckgo.browser.ui.databinding.ItemAutocompleteDefaultBinding
import com.duckduckgo.browser.ui.databinding.ItemAutocompleteDividerBinding
import com.duckduckgo.browser.ui.databinding.ItemAutocompleteDuckaiSuggestionBinding
import com.duckduckgo.browser.ui.databinding.ItemAutocompleteHistorySearchSuggestionBinding
import com.duckduckgo.browser.ui.databinding.ItemAutocompleteHistorySuggestionBinding
import com.duckduckgo.browser.ui.databinding.ItemAutocompleteInAppMessageBinding
import com.duckduckgo.browser.ui.databinding.ItemAutocompleteSearchSuggestionBinding
import com.duckduckgo.browser.ui.databinding.ItemAutocompleteSwitchToTabSuggestionBinding
import com.duckduckgo.browser.ui.omnibar.OmnibarPosition
import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.mobile.android.R as CommonR

interface SuggestionViewHolderFactory {
    fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder

    fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        deleteClickListener: (AutoCompleteSuggestion) -> Unit = {},
        openSettingsClickListener: () -> Unit = {},
        longPressClickListener: (AutoCompleteSuggestion) -> Unit = {},
    )
}

class SearchSuggestionViewHolderFactory(
    private val omnibarPosition: OmnibarPosition,
) : SuggestionViewHolderFactory {
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
        deleteClickListener: (AutoCompleteSuggestion) -> Unit,
        openSettingsClickListener: () -> Unit,
        longPressClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        val searchSuggestionViewHolder = holder as AutoCompleteViewHolder.SearchSuggestionViewHolder
        searchSuggestionViewHolder.bind(
            suggestion as AutoCompleteSearchSuggestion,
            immediateSearchClickListener,
            editableSearchClickListener,
            omnibarPosition,
        )
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
        deleteClickListener: (AutoCompleteSuggestion) -> Unit,
        openSettingsClickListener: () -> Unit,
        longPressClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        val searchSuggestionViewHolder = holder as AutoCompleteViewHolder.HistorySuggestionViewHolder
        searchSuggestionViewHolder.bind(
            suggestion as AutoCompleteHistorySuggestion,
            immediateSearchClickListener,
            longPressClickListener,
        )
    }
}

class HistorySearchSuggestionViewHolderFactory : SuggestionViewHolderFactory {
    override fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAutocompleteHistorySearchSuggestionBinding.inflate(inflater, parent, false)
        return AutoCompleteViewHolder.HistorySearchSuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        deleteClickListener: (AutoCompleteSuggestion) -> Unit,
        openSettingsClickListener: () -> Unit,
        longPressClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        val historySearchSuggestionViewHolder = holder as AutoCompleteViewHolder.HistorySearchSuggestionViewHolder
        historySearchSuggestionViewHolder.bind(
            suggestion as AutoCompleteHistorySearchSuggestion,
            immediateSearchClickListener,
            longPressClickListener,
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
        deleteClickListener: (AutoCompleteSuggestion) -> Unit,
        openSettingsClickListener: () -> Unit,
        longPressClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        val bookmarkSuggestionViewHolder = holder as AutoCompleteViewHolder.BookmarkSuggestionViewHolder
        bookmarkSuggestionViewHolder.bind(
            suggestion as AutoCompleteBookmarkSuggestion,
            immediateSearchClickListener,
        )
    }
}

class SwitchToTabSuggestionViewHolderFactory : SuggestionViewHolderFactory {
    override fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAutocompleteSwitchToTabSuggestionBinding.inflate(inflater, parent, false)
        return AutoCompleteViewHolder.SwitchToTabSuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        deleteClickListener: (AutoCompleteSuggestion) -> Unit,
        openSettingsClickListener: () -> Unit,
        longPressClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        val switchToTabSuggestionViewHolder = holder as AutoCompleteViewHolder.SwitchToTabSuggestionViewHolder
        switchToTabSuggestionViewHolder.bind(
            suggestion as AutoCompleteSwitchToTabSuggestion,
            immediateSearchClickListener,
        )
    }
}

class EmptySuggestionViewHolderFactory : SuggestionViewHolderFactory {
    override fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder {
        val view = View(parent.context)
        view.layoutParams =
            RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT,
            )
        return AutoCompleteViewHolder.EmptySuggestionViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        deleteClickListener: (AutoCompleteSuggestion) -> Unit,
        openSettingsClickListener: () -> Unit,
        longPressClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        // do nothing
    }
}

class DefaultSuggestionViewHolderFactory(
    private val omnibarPosition: OmnibarPosition,
) : SuggestionViewHolderFactory {
    override fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return AutoCompleteViewHolder.DefaultSuggestionViewHolder(
            ItemAutocompleteDefaultBinding.inflate(
                inflater,
                parent,
                false,
            ),
        )
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        deleteClickListener: (AutoCompleteSuggestion) -> Unit,
        openSettingsClickListener: () -> Unit,
        longPressClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        val viewholder = holder as AutoCompleteViewHolder.DefaultSuggestionViewHolder
        viewholder.bind(suggestion as AutoCompleteDefaultSuggestion, immediateSearchClickListener, omnibarPosition)
    }
}

class InAppMessageViewHolderFactory : SuggestionViewHolderFactory {
    override fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return InAppMessageViewHolder(ItemAutocompleteInAppMessageBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        deleteClickListener: (AutoCompleteSuggestion) -> Unit,
        openSettingsClickListener: () -> Unit,
        longPressClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        val viewHolder = holder as InAppMessageViewHolder
        viewHolder.bind(suggestion, deleteClickListener, openSettingsClickListener)
    }
}

class DividerViewHolderFactory : SuggestionViewHolderFactory {
    override fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAutocompleteDividerBinding.inflate(inflater, parent, false)
        return AutoCompleteViewHolder.DividerViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        deleteClickListener: (AutoCompleteSuggestion) -> Unit,
        openSettingsClickListener: () -> Unit,
        longPressClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        // do nothing
    }
}

class DuckAIPromptSuggestionViewHolderFactory : SuggestionViewHolderFactory {
    override fun onCreateViewHolder(parent: ViewGroup): AutoCompleteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemAutocompleteDuckaiSuggestionBinding.inflate(inflater, parent, false)
        return AutoCompleteViewHolder.DuckAIPromptViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AutoCompleteViewHolder,
        suggestion: AutoCompleteSuggestion,
        immediateSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
        deleteClickListener: (AutoCompleteSuggestion) -> Unit,
        openSettingsClickListener: () -> Unit,
        longPressClickListener: (AutoCompleteSuggestion) -> Unit,
    ) {
        val bookmarkSuggestionViewHolder = holder as AutoCompleteViewHolder.DuckAIPromptViewHolder
        bookmarkSuggestionViewHolder.bind(
            suggestion as AutoCompleteSuggestion.AutoCompleteDuckAIPrompt,
            immediateSearchClickListener,
        )
    }
}

sealed class AutoCompleteViewHolder(
    itemView: View,
) : RecyclerView.ViewHolder(itemView) {
    class SearchSuggestionViewHolder(
        val binding: ItemAutocompleteSearchSuggestionBinding,
    ) : AutoCompleteViewHolder(binding.root) {
        fun bind(
            item: AutoCompleteSearchSuggestion,
            immediateSearchListener: (AutoCompleteSuggestion) -> Unit,
            editableSearchClickListener: (AutoCompleteSuggestion) -> Unit,
            omnibarPosition: OmnibarPosition,
        ) = with(binding) {
            phrase.text = item.phrase

            val phraseOrUrlImage =
                if (item.isUrl) com.duckduckgo.mobile.android.R.drawable.ic_globe_24 else com.duckduckgo.mobile.android.R.drawable.ic_find_search_24
            phraseOrUrlIndicator.setImageResource(phraseOrUrlImage)

            editQueryImage.setOnClickListener { editableSearchClickListener(item) }
            root.setOnClickListener { immediateSearchListener(item) }

            if (omnibarPosition == OmnibarPosition.BOTTOM) {
                editQueryImage.setImageResource(R.drawable.ic_arrow_circle_down_left_16)
            }
        }
    }

    class HistorySearchSuggestionViewHolder(
        val binding: ItemAutocompleteHistorySearchSuggestionBinding,
    ) : AutoCompleteViewHolder(binding.root) {
        fun bind(
            item: AutoCompleteHistorySearchSuggestion,
            immediateSearchListener: (AutoCompleteSuggestion) -> Unit,
            longPressClickListener: (AutoCompleteSuggestion) -> Unit,
        ) = with(binding) {
            phrase.text = item.phrase

            root.setOnClickListener { immediateSearchListener(item) }
            root.setOnLongClickListener {
                longPressClickListener(item)
                true
            }
        }
    }

    class BookmarkSuggestionViewHolder(
        val binding: ItemAutocompleteBookmarkSuggestionBinding,
    ) : AutoCompleteViewHolder(binding.root) {
        fun bind(
            item: AutoCompleteBookmarkSuggestion,
            immediateSearchListener: (AutoCompleteSuggestion) -> Unit,
        ) = with(binding) {
            title.text = item.title
            url.text = item.phrase

            bookmarkIndicator.setImageResource(if (item.isFavorite) R.drawable.ic_bookmark_favorite_24 else CommonR.drawable.ic_bookmark_24)
            root.setOnClickListener { immediateSearchListener(item) }
        }
    }

    class HistorySuggestionViewHolder(
        val binding: ItemAutocompleteHistorySuggestionBinding,
    ) : AutoCompleteViewHolder(binding.root) {
        fun bind(
            item: AutoCompleteHistorySuggestion,
            immediateSearchListener: (AutoCompleteSuggestion) -> Unit,
            longPressClickListener: (AutoCompleteSuggestion) -> Unit,
        ) = with(binding) {
            title.text = item.title
            url.text = item.phrase

            root.setOnClickListener { immediateSearchListener(item) }
            root.setOnLongClickListener {
                longPressClickListener(item)
                true
            }
        }
    }

    class SwitchToTabSuggestionViewHolder(
        val binding: ItemAutocompleteSwitchToTabSuggestionBinding,
    ) : AutoCompleteViewHolder(binding.root) {
        fun bind(
            item: AutoCompleteSwitchToTabSuggestion,
            immediateSearchListener: (AutoCompleteSuggestion) -> Unit,
        ) = with(binding) {
            title.text = item.title
            url.text = root.context.getString(R.string.autocompleteSwitchToTab, item.phrase)

            root.setOnClickListener { immediateSearchListener(item) }
        }
    }

    class EmptySuggestionViewHolder(
        view: View,
    ) : AutoCompleteViewHolder(view)

    class DividerViewHolder(
        val binding: ItemAutocompleteDividerBinding,
    ) : AutoCompleteViewHolder(binding.root)

    class DefaultSuggestionViewHolder(
        val binding: ItemAutocompleteDefaultBinding,
    ) : AutoCompleteViewHolder(binding.root) {
        fun bind(
            item: AutoCompleteDefaultSuggestion,
            immediateSearchListener: (AutoCompleteSuggestion) -> Unit,
            omnibarPosition: OmnibarPosition,
        ) {
            binding.phrase.text = item.phrase
            binding.root.setOnClickListener { immediateSearchListener(item) }

            if (omnibarPosition == OmnibarPosition.BOTTOM) {
                binding.editQueryImage.setImageResource(R.drawable.ic_arrow_circle_down_left_16)
            }
        }
    }

    class InAppMessageViewHolder(
        val binding: ItemAutocompleteInAppMessageBinding,
    ) : AutoCompleteViewHolder(binding.root) {
        fun bind(
            item: AutoCompleteSuggestion,
            deleteClickListener: (AutoCompleteSuggestion) -> Unit,
            openSettingsClickListener: () -> Unit,
        ) {
            binding.messageCta.setMessage(
                Message(
                    title = binding.root.context.getString(R.string.improvedAutoCompleteIAMTitle),
                    subtitle = binding.root.context.getString(R.string.improvedAutoCompleteIAMContent),
                    action = binding.root.context.getString(R.string.openAutoCompleteSettings),
                ),
            )
            binding.messageCta.onCloseButtonClicked { deleteClickListener(item) }
            binding.messageCta.onPrimaryActionClicked { openSettingsClickListener() }
        }
    }

    class DuckAIPromptViewHolder(
        val binding: ItemAutocompleteDuckaiSuggestionBinding,
    ) : AutoCompleteViewHolder(binding.root) {
        fun bind(
            item: AutoCompleteSuggestion.AutoCompleteDuckAIPrompt,
            itemClickListener: (AutoCompleteSuggestion) -> Unit,
        ) = with(binding) {
            title.text = item.phrase

            root.setOnClickListener { itemClickListener(item) }
        }
    }
}
