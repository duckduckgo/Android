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

package com.duckduckgo.duckchat.impl.ui.nativeinput.suggestions.reader

import com.duckduckgo.duckchat.impl.feature.DuckAiChatHistoryFeature
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.feature.maxHistoryCount
import com.duckduckgo.duckchat.impl.feature.recentDaysCutoff
import com.duckduckgo.duckchat.impl.models.ChatType
import com.duckduckgo.duckchat.impl.models.toChatType
import com.duckduckgo.duckchat.impl.ui.nativeinput.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject

class ChatSuggestionsNativeReader @Inject constructor(
    private val store: DuckAiChatStore,
    private val feature: DuckAiChatHistoryFeature,
    private val duckChatFeature: DuckChatFeature,
) : ChatSuggestionsReader {

    override suspend fun fetchSuggestions(query: String): List<ChatSuggestion> {
        val typeIconEnabled = duckChatFeature.chatSuggestionTypeIcon().isEnabled()

        return store.getChats()
            .selectChatSuggestions(query, maxSuggestions = feature.maxHistoryCount(), recentDaysCutoff = feature.recentDaysCutoff())
            .map { chat ->
                ChatSuggestion(
                    chatId = chat.chatId,
                    title = chat.title,
                    lastEdit = parseLastEdit(chat.lastEdit),
                    pinned = chat.pinned,
                    type = if (typeIconEnabled) chat.toChatType() else ChatType.Discussion,
                )
            }
    }

    override fun tearDown() = Unit // no WebView to clean up
}

/**
 * The selection rules for Duck.ai chat suggestions read from native storage, shared by every surface that lists them
 * (omnibar autocomplete and the duckduckgo.com homepage). Without a query: pinned chats plus chats edited since the start
 * of the day [recentDaysCutoff] days ago (no cutoff when it is not positive). With a query: chats whose title contains it.
 * Pinned first, then newest first, capped at [maxSuggestions] in total.
 */
internal fun List<DuckAiChat>.selectChatSuggestions(
    query: String,
    maxSuggestions: Int,
    recentDaysCutoff: Int,
): List<DuckAiChat> {
    val recentCutoff = if (recentDaysCutoff > 0) {
        LocalDateTime.now().minusDays(recentDaysCutoff.toLong()).toLocalDate().atStartOfDay()
    } else {
        LocalDateTime.MIN
    }
    return filter { chat ->
        if (query.isEmpty()) {
            chat.pinned || parseLastEdit(chat.lastEdit) >= recentCutoff
        } else {
            chat.title.contains(query, ignoreCase = true)
        }
    }
        .sortedWith(compareByDescending<DuckAiChat> { it.pinned }.thenByDescending { parseLastEdit(it.lastEdit) })
        .take(maxSuggestions)
}

private fun parseLastEdit(lastEditStr: String): LocalDateTime {
    if (lastEditStr.isEmpty()) return LocalDateTime.MIN
    return try {
        LocalDateTime.ofInstant(Instant.parse(lastEditStr), ZoneId.systemDefault())
    } catch (_: DateTimeParseException) {
        LocalDateTime.MIN
    }
}
