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

package com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader

import com.duckduckgo.duckchat.impl.feature.DuckAiChatHistoryFeature
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject

class ChatSuggestionsNativeReader @Inject constructor(
    private val store: DuckAiChatStore,
    private val feature: DuckAiChatHistoryFeature,
) : ChatSuggestionsReader {

    override suspend fun fetchSuggestions(query: String): List<ChatSuggestion> {
        val maxSuggestions = getMaxHistoryCount()
        val recentCutoff = LocalDateTime.now().minusDays(RECENT_DAYS_CUTOFF).toLocalDate().atStartOfDay()

        return store.getChats()
            .filter { chat ->
                if (query.isEmpty()) {
                    chat.pinned || parseLastEdit(chat.lastEdit) >= recentCutoff
                } else {
                    chat.title.contains(query, ignoreCase = true)
                }
            }
            .sortedWith(compareByDescending<DuckAiChat> { it.pinned }.thenByDescending { parseLastEdit(it.lastEdit) })
            .take(maxSuggestions)
            .map { chat ->
                ChatSuggestion(
                    chatId = chat.chatId,
                    title = chat.title,
                    lastEdit = parseLastEdit(chat.lastEdit),
                    pinned = chat.pinned,
                )
            }
    }

    override fun tearDown() = Unit // no WebView to clean up

    private fun getMaxHistoryCount(): Int = runCatching {
        feature.self().getSettings()?.let { JSONObject(it).optInt("maxHistoryCount", DEFAULT_MAX_SUGGESTIONS) }
    }.getOrNull() ?: DEFAULT_MAX_SUGGESTIONS

    private fun parseLastEdit(lastEditStr: String): LocalDateTime {
        if (lastEditStr.isEmpty()) return LocalDateTime.MIN
        return try {
            LocalDateTime.ofInstant(Instant.parse(lastEditStr), ZoneId.systemDefault())
        } catch (_: DateTimeParseException) {
            LocalDateTime.MIN
        }
    }

    companion object {
        private const val DEFAULT_MAX_SUGGESTIONS = 10
        private const val RECENT_DAYS_CUTOFF = 7L
    }
}
