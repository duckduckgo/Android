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

        /**
         * Fetches the enabled [NativeInputChatTabItemPlugin]s and inserts each item's adapter at the top
         * of the ConcatAdapter, preserving the plugin point's (priority) order. Safe to call once per
         * binding; [scope] is handed to each item and must outlive the binding's presentation.
         */
        suspend fun loadPluginItems(context: Context, scope: CoroutineScope) {
            chatItemPlugins.getPlugins().forEach { plugin ->
                val item = plugin.create(context, scope)
                // Each item is added above the previously-added one's successors: inserting at the
                // running plugin count keeps them ordered [first..last] above the built-in sections.
                concatAdapter.addAdapter(pluginItems.size, item.adapter)
                pluginItems += item
            }
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
            val isTyping = query.isNotEmpty()
            val hasChat = suggestions.chatHistory.isNotEmpty()
            val hasUrl = suggestions.urlSuggestions.suggestions.isNotEmpty()
            val showUrl = isTyping && hasUrl
            val showShortcut = isHistoryAvailable && suggestions.chatHistory.size > ChatHistoryShortcutAdapter.VIEW_ALL_CHATS_THRESHOLD

            // Forward the query only to plugins that opted in; static items are left untouched.
            pluginItems.forEach { item ->
                if (item.supportsQuery) item.onQueryChanged(query)
            }
            // A populated plugin item keeps the suggestions overlay open even with no chat/typing.
            val hasPluginContent = pluginItems.any { it.adapter.itemCount > 0 }
            val hasContent = hasChat || isTyping || hasPluginContent

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
