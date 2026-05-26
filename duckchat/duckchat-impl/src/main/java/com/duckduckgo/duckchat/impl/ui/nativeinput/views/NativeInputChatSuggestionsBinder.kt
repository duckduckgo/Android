/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.browser.ui.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenConfigResolver
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatHistoryShortcutAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSearchSuggestionAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestionsAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.SectionDividerAdapter
import com.duckduckgo.duckchat.impl.ui.ChatTabSuggestions
import javax.inject.Inject

private const val VIEW_ALL_CHATS_THRESHOLD = 8

/**
 * Builds the chat-tab ConcatAdapter:
 * chat history → divider → URL suggestions → divider → "Search for [query]" → divider → "View all Chats".
 */
class NativeInputChatSuggestionsBinder @Inject constructor(
    private val inputScreenConfigResolver: InputScreenConfigResolver,
) {

    class Binding internal constructor(
        val adapter: RecyclerView.Adapter<*>,
        private val chatSuggestionsAdapter: ChatSuggestionsAdapter,
        private val urlAdapter: BrowserAutoCompleteSuggestionsAdapter,
        private val urlDivider: SectionDividerAdapter,
        private val searchDivider: SectionDividerAdapter,
        private val searchForAdapter: ChatSearchSuggestionAdapter,
        private val historyShortcutDivider: SectionDividerAdapter,
        private val historyShortcutAdapter: ChatHistoryShortcutAdapter,
    ) {
        /**
         * Applies content to all sub-adapters; [onCommit] fires after the async chat history
         * arrives. Callers must defer attaching the ConcatAdapter to the RecyclerView until then,
         * otherwise the late insert at position 0 leaves the list scrolled to the first rows that arrived.
         * [onCommit]'s `hasContent` reports whether the final list is non-empty.
         */
        fun submit(
            suggestions: ChatTabSuggestions,
            query: String,
            isHistoryAvailable: Boolean,
            onCommit: (hasContent: Boolean) -> Unit,
        ) {
            val isTyping = query.isNotEmpty()
            val hasChat = suggestions.chatHistory.isNotEmpty()
            val hasUrl = suggestions.urlSuggestions.suggestions.isNotEmpty()
            val showUrl = isTyping && hasUrl
            val showShortcut = isHistoryAvailable && suggestions.chatHistory.size > VIEW_ALL_CHATS_THRESHOLD
            val hasContent = hasChat || isTyping

            chatSuggestionsAdapter.submitList(suggestions.chatHistory) {
                onCommit(hasContent)
            }
            if (showUrl) {
                urlAdapter.updateData(suggestions.urlSuggestions.query, suggestions.urlSuggestions.suggestions)
            } else {
                urlAdapter.updateData("", emptyList())
            }
            searchForAdapter.update(query, visible = isTyping)
            urlDivider.setVisible(hasChat && showUrl)
            searchDivider.setVisible((hasChat || showUrl) && isTyping)
            historyShortcutAdapter.setVisible(showShortcut)
            historyShortcutDivider.setVisible(showShortcut)
        }

        fun clear() {
            chatSuggestionsAdapter.submitList(emptyList())
            urlAdapter.updateData("", emptyList())
            searchForAdapter.update("", visible = false)
            urlDivider.setVisible(false)
            searchDivider.setVisible(false)
            historyShortcutAdapter.setVisible(false)
            historyShortcutDivider.setVisible(false)
        }

        @VisibleForTesting
        internal fun historyShortcutAdapter(): ChatHistoryShortcutAdapter = historyShortcutAdapter

        @VisibleForTesting
        internal fun historyShortcutDivider(): SectionDividerAdapter = historyShortcutDivider
    }

    fun create(
        onChatSuggestionSelected: (ChatSuggestion) -> Unit,
        onChatUrlSuggestionClicked: (AutoCompleteSuggestion) -> Unit,
        onSearchForQuerySubmitted: (String) -> Unit,
        onChatHistoryShortcutClicked: () -> Unit,
    ): Binding {
        val chatSuggestionsAdapter = ChatSuggestionsAdapter { onChatSuggestionSelected(it) }
        val urlAdapter = BrowserAutoCompleteSuggestionsAdapter(
            immediateSearchClickListener = { onChatUrlSuggestionClicked(it) },
            editableSearchClickListener = { },
            autoCompleteLongPressClickListener = { },
            omnibarType = if (inputScreenConfigResolver.useTopBar()) OmnibarType.SINGLE_TOP else OmnibarType.SINGLE_BOTTOM,
            hideEditQueryArrow = true,
            hideSectionDividers = true,
        )
        val urlDivider = SectionDividerAdapter()
        val searchDivider = SectionDividerAdapter()
        val searchForAdapter = ChatSearchSuggestionAdapter { onSearchForQuerySubmitted(it) }
        val historyShortcutDivider = SectionDividerAdapter()
        val historyShortcutAdapter = ChatHistoryShortcutAdapter { onChatHistoryShortcutClicked() }

        val concat = ConcatAdapter(
            chatSuggestionsAdapter,
            urlDivider,
            urlAdapter,
            searchDivider,
            searchForAdapter,
            historyShortcutDivider,
            historyShortcutAdapter,
        )

        return Binding(
            adapter = concat,
            chatSuggestionsAdapter = chatSuggestionsAdapter,
            urlAdapter = urlAdapter,
            urlDivider = urlDivider,
            searchDivider = searchDivider,
            searchForAdapter = searchForAdapter,
            historyShortcutDivider = historyShortcutDivider,
            historyShortcutAdapter = historyShortcutAdapter,
        )
    }
}
