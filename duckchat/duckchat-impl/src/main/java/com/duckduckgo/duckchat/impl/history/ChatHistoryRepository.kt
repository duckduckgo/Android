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

import android.content.Context
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

interface ChatHistoryRepository {
    fun observeChats(): Flow<List<ChatHistoryItem>>

    suspend fun deleteChat(chatId: String)
    suspend fun deleteAllChats()
    suspend fun renameChat(chatId: String, newTitle: String): Boolean
    suspend fun setPinned(chatId: String, pinned: Boolean)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealChatHistoryRepository @Inject constructor(
    private val chatStore: DuckAiChatStore,
    private val dispatchers: DispatcherProvider,
    private val context: Context,
) : ChatHistoryRepository {

    private val fallbackTitle: String by lazy { context.getString(R.string.duck_ai_chat_history_untitled) }

    override fun observeChats(): Flow<List<ChatHistoryItem>> =
        chatStore.getChatsFlow()
            .map { chats -> chats.map(::toChatHistoryItem) }

    override suspend fun deleteChat(chatId: String) {
        withContext(dispatchers.io()) { chatStore.deleteChat(chatId) }
    }

    override suspend fun deleteAllChats() {
        withContext(dispatchers.io()) { chatStore.deleteAllChats() }
    }

    override suspend fun renameChat(chatId: String, newTitle: String): Boolean =
        withContext(dispatchers.io()) { chatStore.renameChat(chatId, newTitle) }

    override suspend fun setPinned(chatId: String, pinned: Boolean) {
        withContext(dispatchers.io()) { chatStore.setPinned(chatId, pinned) }
    }

    private fun toChatHistoryItem(chat: DuckAiChat): ChatHistoryItem = ChatHistoryItem(
        chatId = chat.chatId,
        displayTitle = chat.title.takeIf { it.isNotBlank() && it != UPSTREAM_UNTITLED } ?: fallbackTitle,
        type = chat.toChatType(),
        pinned = chat.pinned,
        lastEditMillis = chat.lastEdit.parseIsoMillis(),
    )

    private fun DuckAiChat.toChatType(): ChatType = model.toChatType()

    private fun String.parseIsoMillis(): Long = runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(0L)

    private companion object {
        const val UPSTREAM_UNTITLED = "Untitled Chat"
    }
}

internal fun String.toChatType(): ChatType = when (this) {
    "image-generation" -> ChatType.ImageGeneration
    "voice-mode" -> ChatType.Voice
    else -> ChatType.Discussion
}
