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

package com.duckduckgo.duckchat.store.impl

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.store.api.DuckAiChat
import com.duckduckgo.duckchat.store.api.DuckAiChatStore
import com.duckduckgo.duckchat.store.impl.handler.DuckAiNativeStorageJsMessageHandler.Companion.MIGRATION_KEY
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckAiChatStore @Inject constructor(
    private val settingsDao: DuckAiBridgeSettingsDao,
    private val chatsDao: DuckAiBridgeChatsDao,
    @DuckAiBridgeFilesDir private val filesDirLazy: Lazy<File>,
    private val dispatchers: DispatcherProvider,
) : DuckAiChatStore {

    override suspend fun hasMigrated(): Boolean =
        withContext(dispatchers.io()) { settingsDao.get(MIGRATION_KEY) != null }

    override suspend fun getChats(): List<DuckAiChat> =
        withContext(dispatchers.io()) { chatsDao.getAll().mapNotNull { it.toDuckAiChat() } }

    override suspend fun deleteChat(chatId: String): Boolean =
        withContext(dispatchers.io()) {
            val entity = chatsDao.getById(chatId) ?: return@withContext false
            val fileRefs = runCatching {
                val json = JSONObject(entity.data)
                json.optJSONArray("fileRefs")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                    ?: emptyList()
            }.getOrDefault(emptyList())

            chatsDao.delete(chatId)

            val filesDir = filesDirLazy.get()
            fileRefs.forEach { uuid -> File(filesDir, uuid).delete() }

            true
        }

    private fun DuckAiBridgeChatEntity.toDuckAiChat(): DuckAiChat? = runCatching {
        val json = JSONObject(data)
        val chatId = json.optString("chatId").takeIf { it.isNotEmpty() } ?: return@runCatching null
        DuckAiChat(
            chatId = chatId,
            title = json.optString("title").ifEmpty { "Untitled Chat" },
            model = json.optString("model"),
            lastEdit = json.optString("lastEdit"),
            pinned = json.optBoolean("pinned", false),
            fileRefs = json.optJSONArray("fileRefs")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
        )
    }.getOrNull()
}
