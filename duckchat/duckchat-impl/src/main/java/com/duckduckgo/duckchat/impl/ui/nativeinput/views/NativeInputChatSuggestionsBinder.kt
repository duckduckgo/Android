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

import android.content.Context
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.browser.ui.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.duckchat.api.inputscreen.NativeInputChatTabItem
import com.duckduckgo.duckchat.api.inputscreen.NativeInputChatTabItemPlugin
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenConfigResolver
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatHistoryShortcutAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSearchSuggestionAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestionsAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.SectionDividerAdapter
import com.duckduckgo.duckchat.impl.ui.ChatTabSuggestions
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * Builds the chat-tab ConcatAdapter:
 * [plugin items] → chat history → divider → "View all Chats" → divider → URL suggestions → divider → "Search for [query]".
 *
 * Externally-contributed [NativeInputChatTabItemPlugin]s are inserted at the top, in plugin-point
 * (priority) order, above the built-in sections. See [Binding.loadPluginItems].
 */
class NativeInputChatSuggestionsBinder @Inject constructor(
    private val inputScreenConfigResolver: InputScreenConfigResolver,
    private val chatItemPlugins: ActivePluginPoint<NativeInputChatTabItemPlugin>,
) {

    class Binding internal constructor(
        private val concatAdapter: ConcatAdapter,
        private val chatItemPlugins: ActivePluginPoint<NativeInputChatTabItemPlugin>,
        private val chatSuggestionsAdapter: ChatSuggestionsAdapter,
        private val urlAdapter: BrowserAutoCompleteSuggestionsAdapter,
        private val urlDivider: SectionDividerAdapter,
        private val searchDivider: SectionDividerAdapter,
        private val searchForAdapter: ChatSearchSuggestionAdapter,
        private val historyShortcutDivider: SectionDividerAdapter,
        private val historyShortcutAdapter: ChatHistoryShortcutAdapter,
    ) {
        val adapter: RecyclerView.Adapter<*> get() = concatAdapter

        private val pluginItems = mutableListOf<NativeInputChatTabItem>()

        // The most recent submit, replayed once plugins finish loading so late-arriving items catch up
        // to the current query/visibility/hasContent instead of racing (and losing to) the first submit.
        private var replayLastSubmit: (() -> Unit)? = null

        // True only while applySubmit runs, so [pluginContentObserver] ignores the row changes the host
        // itself drives (those are already reflected in that submit's commit) and reacts only to a plugin
        // changing its own rows out of band — e.g. a card that dismisses itself between submits.
        private var applyingSubmit = false

        // Recomputes hasContent from the latest submit's chat/typing state plus the live plugin rows and
        // re-fires onCommit. Set by applySubmit; invoked by [pluginContentObserver].
        private var recomputeOverlay: (() -> Unit)? = null

        private val pluginContentObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = onPluginContentChanged()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = onPluginContentChanged()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = onPluginContentChanged()
        }

        private fun onPluginContentChanged() {
            if (applyingSubmit) return
            recomputeOverlay?.invoke()
        }

        /**
         * Fetches the enabled [NativeInputChatTabItemPlugin]s and inserts each item's adapter(s) at the
         * top of the ConcatAdapter, preserving the plugin point's (priority) order and, within an item,
         * the order of its [NativeInputChatTabItem.adapters]. Safe to call once per binding; [scope] is
         * handed to each item and must outlive the binding's presentation.
         *
         * Loading races [submit]: both run on the main thread but in separate coroutines, so a [submit]
         * may apply before the items exist. Once they're in we replay the latest [submit] so query
         * forwarding and `hasContent` reflect the plugins; without it a submit that ran first would forward
         * the first query to no item and compute the overlay without the plugin rows.
         */
        suspend fun loadPluginItems(context: Context, scope: CoroutineScope) {
            var insertIndex = 0
            chatItemPlugins.getPlugins().forEach { plugin ->
                val item = plugin.create(context, scope)
                // Insert each item's adapters at the running index so plugins keep priority order and
                // their adapters keep their own order, all above the built-in sections.
                item.adapters.forEach { adapter ->
                    concatAdapter.addAdapter(insertIndex++, adapter)
                    // Observe so a plugin clearing its own rows (e.g. a dismiss) recomputes the overlay.
                    adapter.registerAdapterDataObserver(pluginContentObserver)
                }
                pluginItems += item
            }
            replayLastSubmit?.invoke()
        }

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
            replayLastSubmit = { applySubmit(suggestions, query, isHistoryAvailable, onCommit) }
            applySubmit(suggestions, query, isHistoryAvailable, onCommit)
        }

        private fun applySubmit(
            suggestions: ChatTabSuggestions,
            query: String,
            isHistoryAvailable: Boolean,
            onCommit: (hasContent: Boolean) -> Unit,
        ) {
            applyingSubmit = true
            try {
                val isTyping = query.isNotEmpty()
                val hasChat = suggestions.chatHistory.isNotEmpty()
                val hasUrl = suggestions.urlSuggestions.suggestions.isNotEmpty()
                val showUrl = isTyping && hasUrl
                val showShortcut = isHistoryAvailable && suggestions.chatHistory.size > ChatHistoryShortcutAdapter.VIEW_ALL_CHATS_THRESHOLD

                // Always forward the query; each item decides what to show (e.g. a zero-state card reports
                // no rows once the user types). The host imposes no visibility policy of its own.
                pluginItems.forEach { it.onQueryChanged(query) }

                // hasContent counts the live plugin rows (a plugin item with any rows keeps the overlay
                // open even with no chat/typing). Capture the recompute so an out-of-band plugin change
                // can re-fire onCommit with the same chat/typing state (see [onPluginContentChanged]).
                val commit = { onCommit(hasChat || isTyping || pluginHasContent()) }
                recomputeOverlay = commit

                chatSuggestionsAdapter.submitList(suggestions.chatHistory) { commit() }
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
            } finally {
                applyingSubmit = false
            }
        }

        private fun pluginHasContent(): Boolean = pluginItems.any { item -> item.adapters.any { it.itemCount > 0 } }

        fun clear() {
            chatSuggestionsAdapter.submitList(emptyList())
            urlAdapter.updateData("", emptyList())
            searchForAdapter.update("", visible = false)
            urlDivider.setVisible(false)
            searchDivider.setVisible(false)
            historyShortcutAdapter.setVisible(false)
            historyShortcutDivider.setVisible(false)
            // Plugin items are not cleared here: clear() runs on tab switches and the binding is reused.
            // Each item owns its lifecycle via the scope handed to it in loadPluginItems.
        }
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
            autoCompleteDeleteClickListener = { },
            omnibarType = if (inputScreenConfigResolver.useTopBar()) OmnibarType.SINGLE_TOP else OmnibarType.SINGLE_BOTTOM,
            hideEditQueryArrow = true,
            hideSectionDividers = true,
            isDeleteButtonVisible = false,
        )
        val urlDivider = SectionDividerAdapter()
        val searchDivider = SectionDividerAdapter()
        val searchForAdapter = ChatSearchSuggestionAdapter { onSearchForQuerySubmitted(it) }
        val historyShortcutDivider = SectionDividerAdapter()
        val historyShortcutAdapter = ChatHistoryShortcutAdapter { onChatHistoryShortcutClicked() }

        val concat = ConcatAdapter(
            chatSuggestionsAdapter,
            historyShortcutDivider,
            historyShortcutAdapter,
            urlDivider,
            urlAdapter,
            searchDivider,
            searchForAdapter,
        )

        return Binding(
            concatAdapter = concat,
            chatItemPlugins = chatItemPlugins,
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
