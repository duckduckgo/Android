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

package com.duckduckgo.duckchat.impl.ui.inputscreen.suggestions.reader

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.feature.DuckAiChatHistoryFeature
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.ChatSuggestionsNativeReader
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.duckduckgo.feature.toggles.api.Toggle
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ChatSuggestionsNativeReaderTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val store: DuckAiChatStore = mock()
    private val feature: DuckAiChatHistoryFeature = mock()
    private val toggle: Toggle = mock()
    private lateinit var reader: ChatSuggestionsNativeReader

    @Before
    fun setup() {
        whenever(feature.self()).thenReturn(toggle)
        whenever(toggle.getSettings()).thenReturn("""{"maxHistoryCount":5}""")
        reader = ChatSuggestionsNativeReader(store, feature)
    }

    @Test
    fun `fetchSuggestions returns empty list when store is empty`() = runTest {
        whenever(store.getChats()).thenReturn(emptyList())
        assertTrue(reader.fetchSuggestions().isEmpty())
    }

    @Test
    fun `fetchSuggestions with empty query filters to last 7 days`() = runTest {
        val recent = chatWithLastEdit(Instant.now().minus(1, ChronoUnit.DAYS).toString())
        val old = chatWithLastEdit("2025-01-01T10:00:00.000Z", chatId = "old")
        whenever(store.getChats()).thenReturn(listOf(recent, old))

        val result = reader.fetchSuggestions(query = "")
        assertEquals(1, result.size)
        assertEquals("chat-1", result[0].chatId)
    }

    @Test
    fun `fetchSuggestions with query filters by title ignoring case`() = runTest {
        val match = chatWithLastEdit("2025-01-01T10:00:00.000Z", title = "Hello World")
        val noMatch = chatWithLastEdit("2025-01-01T10:00:00.000Z", chatId = "no-match", title = "Goodbye")
        whenever(store.getChats()).thenReturn(listOf(match, noMatch))

        val result = reader.fetchSuggestions(query = "hello")
        assertEquals(1, result.size)
        assertEquals("chat-1", result[0].chatId)
    }

    @Test
    fun `fetchSuggestions with query does not apply 7-day filter`() = runTest {
        val old = chatWithLastEdit("2025-01-01T10:00:00.000Z", title = "Old but matching")
        whenever(store.getChats()).thenReturn(listOf(old))

        val result = reader.fetchSuggestions(query = "matching")
        assertEquals(1, result.size)
    }

    @Test
    fun `fetchSuggestions sorts pinned chats first then by lastEdit desc`() = runTest {
        val older = chatWithLastEdit("2026-03-01T10:00:00.000Z", chatId = "older")
        val newer = chatWithLastEdit("2026-04-01T10:00:00.000Z", chatId = "newer")
        val pinned = chatWithLastEdit("2026-02-01T10:00:00.000Z", chatId = "pinned", pinned = true)
        whenever(store.getChats()).thenReturn(listOf(older, newer, pinned))

        val result = reader.fetchSuggestions(query = "Test")
        assertEquals("pinned", result[0].chatId)
        assertEquals("newer", result[1].chatId)
        assertEquals("older", result[2].chatId)
    }

    @Test
    fun `fetchSuggestions respects maxHistoryCount from feature settings`() = runTest {
        whenever(toggle.getSettings()).thenReturn("""{"maxHistoryCount":2}""")
        val chats = (1..5).map { chatWithLastEdit(Instant.now().minus(1, ChronoUnit.DAYS).toString(), chatId = "chat-$it") }
        whenever(store.getChats()).thenReturn(chats)

        assertEquals(2, reader.fetchSuggestions().size)
    }

    @Test
    fun `fetchSuggestions uses default max of 10 when settings null`() = runTest {
        whenever(toggle.getSettings()).thenReturn(null)
        val chats = (1..15).map { chatWithLastEdit(Instant.now().minus(1, ChronoUnit.DAYS).toString(), chatId = "chat-$it") }
        whenever(store.getChats()).thenReturn(chats)

        assertEquals(10, reader.fetchSuggestions().size)
    }

    @Test
    fun `tearDown is a no-op`() {
        reader.tearDown() // should not throw
    }

    private fun chatWithLastEdit(
        lastEdit: String,
        chatId: String = "chat-1",
        title: String = "Test",
        pinned: Boolean = false,
    ) = DuckAiChat(
        chatId = chatId,
        title = title,
        model = "gpt-5-mini",
        lastEdit = lastEdit,
        pinned = pinned,
    )
}
