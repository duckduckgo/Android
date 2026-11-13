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

import android.content.Context
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.duckchat.impl.history.toChatHistoryItems
import com.duckduckgo.duckchat.impl.models.ChatType
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.duckduckgo.duckchat.impl.R as DuckChatR

data class DuckAiChatsWidgetChat(
    val chatId: String,
    val title: String,
    val pinned: Boolean,
    val type: ChatType,
)

/** Reads from the regular-mode store only: the widget must never surface Fire-mode chats. */
class DuckAiChatsWidgetChats @Inject constructor(
    private val context: Context,
    @RegularMode private val chatStore: DuckAiChatStore,
) {

    suspend fun getChats(): List<DuckAiChatsWidgetChat> = chatStore.getChats().toWidgetChats(untitledTitle())

    fun observeChats(): Flow<List<DuckAiChatsWidgetChat>> =
        chatStore.getChatsFlow().map { it.toWidgetChats(untitledTitle()) }.distinctUntilChanged()

    private fun untitledTitle(): String = context.getString(DuckChatR.string.duck_ai_chat_history_untitled)
}

fun List<DuckAiChat>.toWidgetChats(
    untitledTitle: String,
    limit: Int = DUCK_AI_CHATS_WIDGET_MAX_CHATS,
): List<DuckAiChatsWidgetChat> =
    toChatHistoryItems(untitledTitle)
        .take(limit)
        .map { DuckAiChatsWidgetChat(chatId = it.chatId, title = it.displayTitle, pinned = it.pinned, type = it.type) }

const val DUCK_AI_CHATS_WIDGET_MAX_CHATS = 20
