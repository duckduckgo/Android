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
package com.duckduckgo.duckchat.impl.history

import com.duckduckgo.duckchat.impl.models.ChatType
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistoryItemsTest {

    @Test
    fun whenChatsMixedThenPinnedFirstAndEachGroupSortedByLastEditDescending() {
        val chats = listOf(
            chat("recent-old", lastEdit = "2026-01-01T00:00:00.000Z"),
            chat("pinned-old", lastEdit = "2026-01-02T00:00:00.000Z", pinned = true),
            chat("recent-new", lastEdit = "2026-03-01T00:00:00.000Z"),
            chat("pinned-new", lastEdit = "2026-02-01T00:00:00.000Z", pinned = true),
        )

        val result = chats.toChatHistoryItems(FALLBACK)

        assertEquals(listOf("pinned-new", "pinned-old", "recent-new", "recent-old"), result.map { it.chatId })
    }

    @Test
    fun whenTitleBlankOrUpstreamUntitledThenFallbackTitleUsed() {
        val chats = listOf(
            chat("blank", title = "  "),
            chat("empty", title = ""),
            chat("upstream", title = "Untitled Chat"),
            chat("named", title = "Trip ideas"),
        )

        val titles = chats.toChatHistoryItems(FALLBACK).associate { it.chatId to it.displayTitle }

        assertEquals(mapOf("blank" to FALLBACK, "empty" to FALLBACK, "upstream" to FALLBACK, "named" to "Trip ideas"), titles)
    }

    @Test
    fun whenLastEditUnparseableThenZeroMillisAndSortedLast() {
        val chats = listOf(
            chat("broken", lastEdit = "not-a-date"),
            chat("valid", lastEdit = "2026-01-01T00:00:00.000Z"),
        )

        val result = chats.toChatHistoryItems(FALLBACK)

        assertEquals(listOf("valid", "broken"), result.map { it.chatId })
        assertEquals(0L, result.last().lastEditMillis)
    }

    @Test
    fun whenChatMappedThenFieldsAreCopied() {
        val item = listOf(chat("a", pinned = true, isVoice = true)).toChatHistoryItems(FALLBACK).single()

        assertEquals(ChatHistoryItem("a", "a", ChatType.Voice, "model", true, 1_767_225_600_000L), item)
    }

    private fun chat(
        id: String,
        title: String = id,
        lastEdit: String = "2026-01-01T00:00:00.000Z",
        pinned: Boolean = false,
        isVoice: Boolean = false,
    ) = DuckAiChat(chatId = id, title = title, model = "model", lastEdit = lastEdit, pinned = pinned, isVoice = isVoice)

    private companion object {
        const val FALLBACK = "Untitled chat"
    }
}
