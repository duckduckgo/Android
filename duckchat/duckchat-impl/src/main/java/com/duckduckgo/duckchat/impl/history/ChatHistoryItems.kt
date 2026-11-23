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

import com.duckduckgo.duckchat.impl.models.toChatType
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import java.time.Instant

/**
 * Maps stored chats to history items, pinned first and each group by last edit descending.
 * Shared by the native chat history and the home-screen widget so both list chats identically.
 */
internal fun List<DuckAiChat>.toChatHistoryItems(fallbackTitle: String): List<ChatHistoryItem> {
    val (pinned, recent) = map { it.toChatHistoryItem(fallbackTitle) }.partition { it.pinned }
    return pinned.sortedByDescending { it.lastEditMillis } + recent.sortedByDescending { it.lastEditMillis }
}

private fun DuckAiChat.toChatHistoryItem(fallbackTitle: String): ChatHistoryItem = ChatHistoryItem(
    chatId = chatId,
    displayTitle = title.takeIf { it.isNotBlank() && it != UPSTREAM_UNTITLED } ?: fallbackTitle,
    type = toChatType(),
    model = model,
    pinned = pinned,
    lastEditMillis = lastEdit.parseIsoMillis(),
)

private fun String.parseIsoMillis(): Long = runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(0L)

private const val UPSTREAM_UNTITLED = "Untitled Chat"
