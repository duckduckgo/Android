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

import android.widget.FrameLayout
import androidx.recyclerview.widget.ConcatAdapter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteResult
import com.duckduckgo.browser.ui.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.InputScreenConfigResolver
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatHistoryShortcutAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.SectionDividerAdapter
import com.duckduckgo.duckchat.impl.models.ChatType
import com.duckduckgo.duckchat.impl.ui.ChatTabSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import com.duckduckgo.mobile.android.R as MobileR

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class NativeInputChatSuggestionsBinderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val inputScreenConfigResolver: InputScreenConfigResolver = mock()
    private lateinit var binder: NativeInputChatSuggestionsBinder

    @Before
    fun setUp() {
        context.setTheme(MobileR.style.Theme_DuckDuckGo_Light)
        whenever(inputScreenConfigResolver.useTopBar()).thenReturn(false)
        binder = NativeInputChatSuggestionsBinder(inputScreenConfigResolver)
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
    fun whenShortcutClickedThenCallbackInvoked() {
        var clicked = false
        val binding = binder.create(
            onChatSuggestionSelected = {},
            onChatUrlSuggestionClicked = {},
            onSearchForQuerySubmitted = {},
            onChatHistoryShortcutClicked = { clicked = true },
        )

        val shortcut = binding.shortcutAdapter()
        shortcut.setVisible(true)

        val parent = FrameLayout(context)
        val holder = shortcut.onCreateViewHolder(parent, 0)
        shortcut.onBindViewHolder(holder, 0)
        holder.itemView.performClick()

        assertTrue(clicked)
    }

    @Test
    fun whenConcatAdapterAssembledThenHistoryShortcutAppearsBeforeUrlSuggestions() {
        val binding = binder.create(
            onChatSuggestionSelected = {},
            onChatUrlSuggestionClicked = {},
            onSearchForQuerySubmitted = {},
            onChatHistoryShortcutClicked = {},
        )

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
}
