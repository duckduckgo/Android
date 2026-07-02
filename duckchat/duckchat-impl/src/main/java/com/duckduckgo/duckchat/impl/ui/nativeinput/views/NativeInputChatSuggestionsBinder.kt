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
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.inputscreen.NativeInputChatTabItem
import com.duckduckgo.duckchat.api.inputscreen.NativeInputChatTabItemPlugin
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
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
    private val inputModeState: DuckChatInputModeState,
    private val duckChatFeature: DuckChatFeature,
    private val duckAiFeatureState: DuckAiFeatureState,
) {

    class Binding internal constructor(
        private val concatAdapter: ConcatAdapter,
        private val chatItemPlugins: ActivePluginPoint<NativeInputChatTabItemPlugin>,
        private val inputModeState: DuckChatInputModeState,
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

        // Recomputes hasContent from the latest submit's chat/typing state plus the live plugin rows and
        // re-fires onCommit. Set by submit; invoked by [pluginContentObserver] when a plugin changes its
        // own rows (items drive their rows from the shared input state they observe, not a host push).
        private var recomputeOverlay: (() -> Unit)? = null

        private val pluginContentObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = onPluginContentChanged()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = onPluginContentChanged()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = onPluginContentChanged()
        }

        private fun onPluginContentChanged() {
            recomputeOverlay?.invoke()
        }

        /**
         * Fetches the enabled [NativeInputChatTabItemPlugin]s and inserts each item's adapter(s) at the
         * top of the ConcatAdapter, preserving the plugin point's (priority) order and, within an item,
         * the order of its [NativeInputChatTabItem.adapters]. Safe to call once per binding; [scope] is
         * handed to each item and must outlive the binding's presentation.
         *
         * Items render from the shared input state (query, mode) they observe — the host pushes nothing —
         * so an item created after a submit self-initialises from that state, and [pluginContentObserver]
         * folds its rows into hasContent. There's no load-vs-submit ordering to manage.
         */
        suspend fun loadPluginItems(context: Context, scope: CoroutineScope) {
            var insertIndex = 0
            chatItemPlugins.getPlugins().forEach { plugin ->
                val item = plugin.create(context, scope)
                // Insert each item's adapters at the running index so plugins keep priority order and
                // their adapters keep their own order, all above the built-in sections.
                item.adapters.forEach { adapter ->
                    concatAdapter.addAdapter(insertIndex++, adapter)
                    // Observe so a plugin changing its own rows (a dismiss, or a query-driven hide)
                    // recomputes the overlay.
                    adapter.registerAdapterDataObserver(pluginContentObserver)
                }
                pluginItems += item
            }
            // If a submit already ran, fold any rows the just-loaded items present into hasContent.
            // This only recomputes the overlay from the last submit's chat/typing state plus live plugin
            // rows — it does not re-apply the query, so there's no stale-query risk.
            recomputeOverlay?.invoke()
        }

        /**
         * Applies content to the built-in sub-adapters; [onCommit] fires after the async chat history
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

            // hasContent counts the live plugin rows (a plugin item with any rows keeps the overlay open
            // even with no chat/typing). The recompute reads the LIVE query, not this submit's captured
            // one: a zero-state item hides as soon as the user types (inputQuery updates before the typed
            // query's fetch submits), and recomputing with a stale isTyping=false would wrongly close the
            // overlay for a frame. The live inputQuery still distinguishes a genuine empty-state dismiss.
            val commit = { onCommit(hasChat || inputModeState.inputQuery.value.isNotEmpty() || pluginHasContent()) }
            recomputeOverlay = commit

            chatSuggestionsAdapter.submitList(suggestions.chatHistory) {
                commit()
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

        private fun pluginHasContent(): Boolean = pluginItems.any { item -> item.adapters.any { it.itemCount > 0 } }

        fun clear() {
            chatSuggestionsAdapter.submitList(emptyList())
            urlAdapter.updateData("", emptyList())
            searchForAdapter.update("", visible = false)
            urlDivider.setVisible(false)
            searchDivider.setVisible(false)
            historyShortcutAdapter.setVisible(false)
            historyShortcutDivider.setVisible(false)
            // Drop the overlay recompute so a plugin adapter notifying after a clear (e.g. async work
            // finishing on a tab switch) can't re-show suggestions we just cleared. The next submit
            // re-arms it. Plugin items themselves are not cleared — clear() runs on tab switches and
            // the binding is reused; each item owns its lifecycle via the scope from loadPluginItems.
            recomputeOverlay = null
        }
    }

    fun create(
        onChatSuggestionSelected: (ChatSuggestion) -> Unit,
        onChatUrlSuggestionClicked: (AutoCompleteSuggestion) -> Unit,
        onSearchForQuerySubmitted: (String) -> Unit,
        onChatHistoryShortcutClicked: () -> Unit,
        onChatSuggestionDeleteClicked: (ChatSuggestion) -> Unit = {},
        onChatUrlSuggestionDeleteClicked: (AutoCompleteSuggestion) -> Unit = {},
    ): Binding {
        val removeChatHistoryEnabled = duckChatFeature.removeChatHistory().isEnabled()
        val chatSuggestionsAdapter = ChatSuggestionsAdapter(
            // Only show the per-row delete (fire) icon when deletion will actually run. The delete
            // path (clearSelectedDuckAiChats) no-ops unless showClearDuckAIChatHistory is on, so
            // gating the icon on the same signal avoids a delete that looks done but did nothing.
            showDeleteButton = removeChatHistoryEnabled && duckAiFeatureState.showClearDuckAIChatHistory.value,
            onChatClicked = { onChatSuggestionSelected(it) },
            onDeleteClicked = { onChatSuggestionDeleteClicked(it) },
        )
        val urlAdapter = BrowserAutoCompleteSuggestionsAdapter(
            immediateSearchClickListener = { onChatUrlSuggestionClicked(it) },
            editableSearchClickListener = { },
            autoCompleteDeleteClickListener = { if (removeChatHistoryEnabled) onChatUrlSuggestionDeleteClicked(it) },
            omnibarType = if (inputScreenConfigResolver.useTopBar()) OmnibarType.SINGLE_TOP else OmnibarType.SINGLE_BOTTOM,
            hideEditQueryArrow = true,
            hideSectionDividers = true,
            isDeleteButtonVisible = removeChatHistoryEnabled,
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
            inputModeState = inputModeState,
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
