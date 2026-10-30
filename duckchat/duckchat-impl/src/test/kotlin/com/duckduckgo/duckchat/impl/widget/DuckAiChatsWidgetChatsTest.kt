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
package com.duckduckgo.duckchat.impl.widget

import com.duckduckgo.duckchat.impl.models.ChatType
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import org.junit.Assert.assertEquals
import org.junit.Test

class DuckAiChatsWidgetChatsTest {

    @Test
    fun whenChatsMixedThenPinnedFirstAndEachGroupSortedByLastEditDescending() {
        val chats = listOf(
            chat("recent-old", lastEdit = "2026-01-01T00:00:00.000Z"),
            chat("pinned-old", lastEdit = "2026-01-02T00:00:00.000Z", pinned = true),
            chat("recent-new", lastEdit = "2026-03-01T00:00:00.000Z"),
            chat("pinned-new", lastEdit = "2026-02-01T00:00:00.000Z", pinned = true),
        )

        val result = chats.toWidgetChats(UNTITLED)

        assertEquals(listOf("pinned-new", "pinned-old", "recent-new", "recent-old"), result.map { it.chatId })
        assertEquals(listOf(true, true, false, false), result.map { it.pinned })
    }

    @Test
    fun whenTitleBlankOrUpstreamUntitledThenFallbackTitleUsed() {
        val chats = listOf(
            chat("blank", title = "  "),
            chat("upstream", title = "Untitled Chat"),
            chat("named", title = "Trip ideas"),
        )

        val result = chats.toWidgetChats(UNTITLED)

        assertEquals(setOf(UNTITLED, "Trip ideas"), result.map { it.title }.toSet())
        assertEquals("Trip ideas", result.single { it.chatId == "named" }.title)
        assertEquals(UNTITLED, result.single { it.chatId == "blank" }.title)
        assertEquals(UNTITLED, result.single { it.chatId == "upstream" }.title)
    }

    @Test
    fun whenLastEditUnparseableThenChatSortedLast() {
        val chats = listOf(
            chat("broken", lastEdit = "not-a-date"),
            chat("valid", lastEdit = "2026-01-01T00:00:00.000Z"),
        )

        assertEquals(listOf("valid", "broken"), chats.toWidgetChats(UNTITLED).map { it.chatId })
    }

    @Test
    fun whenMoreChatsThanLimitThenPinnedKeptAndOldestRecentDropped() {
        val pinned = chat("pinned", lastEdit = "2025-01-01T00:00:00.000Z", pinned = true)
        val recent = (1..5).map { chat("c$it", lastEdit = "2026-01-0${it}T00:00:00.000Z") }

        val result = (recent + pinned).toWidgetChats(UNTITLED, limit = 3)

        assertEquals(listOf("pinned", "c5", "c4"), result.map { it.chatId })
    }

    @Test
    fun whenNoChatsThenEmpty() {
        assertEquals(emptyList<DuckAiChatsWidgetChat>(), emptyList<DuckAiChat>().toWidgetChats(UNTITLED))
    }

    @Test
    fun whenChatFlagsSetThenTypeFollowsNativeDerivation() {
        val chats = listOf(
            chat("discussion", lastEdit = "2026-01-01T00:00:01.000Z"),
            chat("image", lastEdit = "2026-01-01T00:00:02.000Z", isImageGeneration = true),
            chat("voice", lastEdit = "2026-01-01T00:00:03.000Z", isVoice = true),
            chat("both", lastEdit = "2026-01-01T00:00:04.000Z", isImageGeneration = true, isVoice = true),
        )

        val result = chats.toWidgetChats(UNTITLED).associate { it.chatId to it.type }

        assertEquals(ChatType.Discussion, result["discussion"])
        assertEquals(ChatType.ImageGeneration, result["image"])
        assertEquals(ChatType.Voice, result["voice"])
        assertEquals(ChatType.ImageGeneration, result["both"])
    }

    private fun chat(
        id: String,
        title: String = id,
        lastEdit: String = "2026-01-01T00:00:00.000Z",
        pinned: Boolean = false,
        isImageGeneration: Boolean = false,
        isVoice: Boolean = false,
    ) = DuckAiChat(
        chatId = id,
        title = title,
        model = "model",
        lastEdit = lastEdit,
        pinned = pinned,
        isImageGeneration = isImageGeneration,
        isVoice = isVoice,
    )

    private companion object {
        const val UNTITLED = "Untitled chat"
    }
}
