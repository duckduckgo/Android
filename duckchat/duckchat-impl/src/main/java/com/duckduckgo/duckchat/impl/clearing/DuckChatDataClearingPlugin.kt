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

package com.duckduckgo.duckchat.impl.clearing

import androidx.core.net.toUri
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.DuckChatConstants.CHAT_ID_PARAM
import com.duckduckgo.duckchat.impl.sync.DuckChatSyncRepository
import com.duckduckgo.sync.api.engine.SyncEngine
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DuckChatDataClearingPlugin @Inject constructor(
    private val duckChatDeleter: DuckChatDeleter,
    private val duckChatSyncRepository: DuckChatSyncRepository,
    private val syncEngine: SyncEngine,
    private val duckChat: DuckChat,
) : DataClearingPlugin {

    override suspend fun onClearData(types: Set<ClearableData>) {
        types.forEach { type ->
            when (type) {
                is ClearableData.DuckChats.All -> deleteAllChats()
                is ClearableData.DuckChats.Single -> deleteChat(type.chatUrl)
                else -> { /* not handled by this plugin */ }
            }
        }
    }

    private suspend fun deleteAllChats() {
        logcat { "DuckChatDataClearingPlugin: deleting all chats" }
        val deleted = duckChatDeleter.deleteAllChats()
        if (deleted) {
            duckChatSyncRepository.recordDuckAiChatsDeleted(System.currentTimeMillis())
            duckChatSyncRepository.clearPendingChatDeletions()
            syncEngine.triggerSync(SyncEngine.SyncTrigger.DATA_CHANGE)
        }
    }

    private suspend fun deleteChat(chatUrl: String) {
        logcat { "DuckChatDataClearingPlugin: deleting chat url=$chatUrl" }
        val chatId = extractChatId(chatUrl) ?: return
        val deleted = duckChatDeleter.deleteChat(chatId)
        if (deleted) {
            duckChatSyncRepository.recordSingleChatDeletion(chatId)
            syncEngine.triggerSync(SyncEngine.SyncTrigger.DATA_CHANGE)
        }
    }

    private fun extractChatId(url: String): String? {
        val uri = url.toUri()
        if (!duckChat.isDuckChatUrl(uri)) return null
        return uri.getQueryParameter(CHAT_ID_PARAM)?.takeIf { it.isNotBlank() }
    }
}
