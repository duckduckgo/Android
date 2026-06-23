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
import android.view.ViewGroup
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteResult
import com.duckduckgo.browser.ui.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.duckchat.api.inputscreen.NativeInputChatTabItem
import com.duckduckgo.duckchat.api.inputscreen.NativeInputChatTabItemPlugin
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenConfigResolver
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatHistoryShortcutAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestionsAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.SectionDividerAdapter
import com.duckduckgo.duckchat.impl.models.ChatType
import com.duckduckgo.duckchat.impl.ui.ChatTabSuggestions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class NativeInputChatSuggestionsBinderTest {
    private val inputScreenConfigResolver: InputScreenConfigResolver = mock()
    private lateinit var binder: NativeInputChatSuggestionsBinder

    @Before
    fun setUp() {
        whenever(inputScreenConfigResolver.useTopBar()).thenReturn(false)
        binder = binderWith()
    }

    @Test
    fun whenChatHistoryCountGreaterThanThresholdAndHistoryAvailableThenShortcutVisible() {
        val binding = createBinding()

        binding.submit(
            suggestions = ChatTabSuggestions(
                chatHistory = chatSuggestions(9),
                urlSuggestions = AutoCompleteResult(query = "", suggestions = emptyList()),
            ),
            query = "",
            isHistoryAvailable = true,
            onCommit = {},
        )

        assertEquals(1, binding.shortcutAdapter().itemCount)
        assertEquals(1, binding.shortcutDivider().itemCount)
    }

    @Test
    fun whenChatHistoryCountEqualsThresholdThenShortcutHidden() {
        val binding = createBinding()

        binding.submit(
            suggestions = ChatTabSuggestions(
                chatHistory = chatSuggestions(8),
                urlSuggestions = AutoCompleteResult(query = "", suggestions = emptyList()),
            ),
            query = "",
            isHistoryAvailable = true,
            onCommit = {},
        )

        assertEquals(0, binding.shortcutAdapter().itemCount)
        assertEquals(0, binding.shortcutDivider().itemCount)
    }

    @Test
    fun whenHistoryNotAvailableThenShortcutHiddenEvenIfCountGreaterThanThreshold() {
        val binding = createBinding()

        binding.submit(
            suggestions = ChatTabSuggestions(
                chatHistory = chatSuggestions(9),
                urlSuggestions = AutoCompleteResult(query = "", suggestions = emptyList()),
            ),
            query = "",
            isHistoryAvailable = false,
            onCommit = {},
        )

        assertEquals(0, binding.shortcutAdapter().itemCount)
        assertEquals(0, binding.shortcutDivider().itemCount)
    }

    @Test
    fun whenConcatAdapterAssembledThenHistoryShortcutAppearsBeforeUrlSuggestions() {
        val binding = createBinding()

        val concat = binding.adapter as ConcatAdapter
        val adapters = concat.adapters
        val shortcutIndex = adapters.indexOfFirst { it is ChatHistoryShortcutAdapter }
        val urlAdapterIndex = adapters.indexOfFirst { it is BrowserAutoCompleteSuggestionsAdapter }

        assertTrue("Shortcut should be before URL suggestions in the ConcatAdapter", shortcutIndex < urlAdapterIndex)
    }

    @Test
    fun whenClearThenAllAdaptersHidden() {
        val binding = createBinding()

        binding.submit(
            suggestions = ChatTabSuggestions(
                chatHistory = chatSuggestions(9),
                urlSuggestions = AutoCompleteResult(query = "", suggestions = emptyList()),
            ),
            query = "",
            isHistoryAvailable = true,
            onCommit = {},
        )
        assertEquals(1, binding.shortcutAdapter().itemCount)

        binding.clear()

        assertEquals(0, binding.shortcutAdapter().itemCount)
        assertEquals(0, binding.shortcutDivider().itemCount)
    }

    @Test
    fun whenPluginItemsLoadedThenAdaptersInsertedAtTopInPluginPointOrder() = runTest {
        val first = fakeItem(countingAdapter(1))
        val second = fakeItem(countingAdapter(1))
        binder = binderWith(fakePlugin(first), fakePlugin(second))
        val binding = createBinding()

        binding.loadPluginItems(mock(), scope)

        val adapters = (binding.adapter as ConcatAdapter).adapters
        assertEquals(first.adapters.single(), adapters[0])
        assertEquals(second.adapters.single(), adapters[1])
        // built-in sections follow the contributed items
        assertTrue(adapters.indexOfFirst { it is ChatSuggestionsAdapter } > 1)
    }

    @Test
    fun whenItemHasMultipleAdaptersThenAllInsertedInOrderAboveBuiltInSections() = runTest {
        val a0 = countingAdapter(1)
        val a1 = countingAdapter(1)
        binder = binderWith(fakePlugin(fakeItem(a0, a1)))
        val binding = createBinding()

        binding.loadPluginItems(mock(), scope)

        val adapters = (binding.adapter as ConcatAdapter).adapters
        assertEquals(a0, adapters[0])
        assertEquals(a1, adapters[1])
        assertTrue(adapters.indexOfFirst { it is ChatSuggestionsAdapter } > 1)
    }

    @Test
    fun whenQueryChangesThenForwardedToAllItems() = runTest {
        val first = fakeItem(countingAdapter(1))
        val second = fakeItem(countingAdapter(1))
        binder = binderWith(fakePlugin(first), fakePlugin(second))
        val binding = createBinding()
        binding.loadPluginItems(mock(), scope)

        submitQuery(binding, "hello")

        assertEquals(listOf("hello"), first.observedQueries)
        assertEquals(listOf("hello"), second.observedQueries)
    }

    @Test
    fun whenPluginItemHasRowsThenOverlayKeptOpenWithNoChatOrTyping() = runTest {
        binder = binderWith(fakePlugin(fakeItem(countingAdapter(1))))
        val binding = createBinding()
        binding.loadPluginItems(mock(), scope)

        var hasContent: Boolean? = null
        binding.submit(
            suggestions = ChatTabSuggestions(
                chatHistory = emptyList(),
                urlSuggestions = AutoCompleteResult(query = "", suggestions = emptyList()),
            ),
            query = "",
            isHistoryAvailable = true,
            onCommit = { hasContent = it },
        )

        assertTrue(hasContent == true)
    }

    @Test
    fun whenPluginItemHasNoRowsAndNoChatOrTypingThenOverlayNotKeptOpen() = runTest {
        binder = binderWith(fakePlugin(fakeItem(countingAdapter(0))))
        val binding = createBinding()
        binding.loadPluginItems(mock(), scope)

        var hasContent: Boolean? = null
        binding.submit(
            suggestions = ChatTabSuggestions(
                chatHistory = emptyList(),
                urlSuggestions = AutoCompleteResult(query = "", suggestions = emptyList()),
            ),
            query = "",
            isHistoryAvailable = true,
            onCommit = { hasContent = it },
        )

        assertFalse(hasContent == true)
    }

    @Test
    fun whenSubmitRunsBeforePluginsLoadThenLatestQueryForwardedAfterLoad() = runTest {
        val item = fakeItem(countingAdapter(1))
        binder = binderWith(fakePlugin(item))
        val binding = createBinding()

        // submit wins the race: runs before loadPluginItems, so pluginItems is still empty here.
        submitQuery(binding, "hello")
        binding.loadPluginItems(mock(), scope)

        assertEquals(listOf("hello"), item.observedQueries)
    }

    @Test
    fun whenPluginContentLoadsAfterSubmitThenOverlayReopenedViaReplay() = runTest {
        binder = binderWith(fakePlugin(fakeItem(countingAdapter(1))))
        val binding = createBinding()

        val committed = mutableListOf<Boolean>()
        // Empty query, no chat history: before plugins load hasContent is false (overlay would hide).
        binding.submit(
            suggestions = ChatTabSuggestions(
                chatHistory = emptyList(),
                urlSuggestions = AutoCompleteResult(query = "", suggestions = emptyList()),
            ),
            query = "",
            isHistoryAvailable = true,
            onCommit = { committed += it },
        )
        binding.loadPluginItems(mock(), scope)

        assertEquals("first commit before load has no content, replay after load reopens", listOf(false, true), committed)
    }

    private fun submitQuery(
        binding: NativeInputChatSuggestionsBinder.Binding,
        query: String,
    ) {
        binding.submit(
            suggestions = ChatTabSuggestions(
                chatHistory = emptyList(),
                urlSuggestions = AutoCompleteResult(query = query, suggestions = emptyList()),
            ),
            query = query,
            isHistoryAvailable = true,
            onCommit = {},
        )
    }

    private val scope = CoroutineScope(SupervisorJob())

    private fun binderWith(vararg plugins: NativeInputChatTabItemPlugin): NativeInputChatSuggestionsBinder =
        NativeInputChatSuggestionsBinder(
            inputScreenConfigResolver,
            object : ActivePluginPoint<NativeInputChatTabItemPlugin> {
                override suspend fun getPlugins(): Collection<NativeInputChatTabItemPlugin> = plugins.toList()
            },
        )

    private fun createBinding(): NativeInputChatSuggestionsBinder.Binding =
        binder.create(
            onChatSuggestionSelected = {},
            onChatUrlSuggestionClicked = {},
            onSearchForQuerySubmitted = {},
            onChatHistoryShortcutClicked = {},
        )

    private fun chatSuggestions(count: Int): List<ChatSuggestion> =
        (1..count).map {
            ChatSuggestion(
                chatId = "id-$it",
                title = "Chat $it",
                lastEdit = LocalDateTime.now(),
                pinned = false,
                type = ChatType.Discussion,
            )
        }

    private fun NativeInputChatSuggestionsBinder.Binding.shortcutAdapter(): ChatHistoryShortcutAdapter {
        val concat = adapter as ConcatAdapter
        return concat.adapters.filterIsInstance<ChatHistoryShortcutAdapter>().single()
    }

    private fun NativeInputChatSuggestionsBinder.Binding.shortcutDivider(): SectionDividerAdapter {
        val concat = adapter as ConcatAdapter
        val adapters = concat.adapters
        val shortcutIndex = adapters.indexOfFirst { it is ChatHistoryShortcutAdapter }
        check(shortcutIndex > 0) { "ChatHistoryShortcutAdapter should not be first in the ConcatAdapter" }
        return adapters[shortcutIndex - 1] as SectionDividerAdapter
    }

    private fun fakePlugin(item: NativeInputChatTabItem): NativeInputChatTabItemPlugin =
        object : NativeInputChatTabItemPlugin {
            override fun create(context: Context, scope: CoroutineScope): NativeInputChatTabItem = item
        }

    private fun fakeItem(vararg adapters: RecyclerView.Adapter<*>): FakeChatItem = FakeChatItem(adapters.toList())

    private fun countingAdapter(count: Int): RecyclerView.Adapter<*> =
        object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
                throw UnsupportedOperationException("not used in tests")

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit

            override fun getItemCount(): Int = count
        }

    private class FakeChatItem(
        override val adapters: List<RecyclerView.Adapter<*>>,
    ) : NativeInputChatTabItem {
        val observedQueries = mutableListOf<String>()
        override fun onQueryChanged(query: String) {
            observedQueries += query
        }
    }
}
