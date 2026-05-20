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
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaDao
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import logcat.logcat
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

/**
 * Stable metadata extracted from FE-owned chat JSON.
 * Only top-level fields that are unlikely to change are parsed here.
 * Message content is intentionally excluded — it is complex and FE-owned.
 */
data class DuckAiChat(
    val chatId: String,
    val title: String,
    val model: String,
    /** ISO-8601 string as stored by the FE, e.g. "2026-04-01T21:31:54.260Z" */
    val lastEdit: String,
    val pinned: Boolean,
    /** UUIDs of files referenced by this chat, stored in the native file store */
    val fileRefs: List<String> = emptyList(),
)

interface DuckAiChatStore {
    /** True once the FE has completed migration of localStorage/IDB to native storage. */
    suspend fun hasMigrated(): Boolean

    /** Returns all chats currently in the native store. Skips entries with malformed JSON or missing chatId. */
    suspend fun getChats(): List<DuckAiChat>

    /** Reactive equivalent of [getChats]. Re-emits on insert/update/delete via Room's Flow contract. */
    fun getChatsFlow(): Flow<List<DuckAiChat>>

    /** Deletes the chat with [chatId] and its associated files. Returns true if the chat existed, false if not found. */
    suspend fun deleteChat(chatId: String): Boolean

    /** Deletes all chats and their associated files. */
    suspend fun deleteAllChats()

    /** Renames [chatId] in the FE-owned JSON blob. Returns true if the chat existed and was updated, false otherwise. */
    suspend fun renameChat(chatId: String, newTitle: String): Boolean

    /** Pins [chatId]. Silent no-op if the chat is not found or the stored JSON is malformed. */
    suspend fun pinChat(chatId: String)

    /** Unpins [chatId]. Silent no-op if the chat is not found or the stored JSON is malformed. */
    suspend fun unpinChat(chatId: String)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckAiChatStore @Inject constructor(
    private val chatsDao: DuckAiBridgeChatsDao,
    private val fileMetaDao: DuckAiBridgeFileMetaDao,
    @param:DuckAiBridgeFilesDir private val filesDirLazy: Lazy<File>,
    private val dispatchers: DispatcherProvider,
    private val migrationPrefs: DuckAiMigrationPrefs,
) : DuckAiChatStore {

    override suspend fun hasMigrated(): Boolean =
        withContext(dispatchers.io()) {
            migrationPrefs.isMigrationDone(DuckAiMigrationPrefs.CHATS_KEY)
        }

    override suspend fun getChats(): List<DuckAiChat> =
        withContext(dispatchers.io()) { chatsDao.getAll().mapNotNull { it.toDuckAiChat() } }

    override fun getChatsFlow(): Flow<List<DuckAiChat>> =
        chatsDao.getAllAsFlow().map { entities -> entities.mapNotNull { it.toDuckAiChat() } }

    override suspend fun deleteChat(chatId: String): Boolean =
        withContext(dispatchers.io()) {
            logcat { "DuckAI: RealDuckAiChatStore.deleteChat($chatId)" }
            val entity = chatsDao.getById(chatId) ?: return@withContext false
            val fileRefs = runCatching {
                val json = JSONObject(entity.data)
                json.optJSONArray("fileRefs")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                    ?: emptyList()
            }.getOrDefault(emptyList())

            chatsDao.delete(chatId)

            val filesDir = filesDirLazy.get()
            fileRefs.forEach { uuid ->
                val file = File(filesDir, uuid)
                if (file.canonicalPath.startsWith(filesDir.canonicalPath + File.separator)) {
                    logcat { "DuckAI: RealDuckAiChatStore.deleteChat($chatId) -- deleting fileRef file=${file.name}" }
                    file.delete()
                    fileMetaDao.delete(uuid)
                } else {
                    logcat { "DuckAI: RealDuckAiChatStore.deleteChat($chatId) -- invalid uuid=$uuid" }
                }
            }

            true
        }

    override suspend fun deleteAllChats() {
        withContext(dispatchers.io()) {
            logcat { "DuckAI: RealDuckAiChatStore.deleteAllChats()...deleting all chats" }
            chatsDao.deleteAll()
            val filesDir = filesDirLazy.get()
            fileMetaDao.getAll().forEach { meta ->
                val file = File(filesDir, meta.uuid)
                if (file.canonicalPath.startsWith(filesDir.canonicalPath + File.separator)) {
                    logcat { "DuckAI: deleting chat file $file" }
                    file.delete()
                }
            }
            fileMetaDao.deleteAll()
        }
    }

    override suspend fun renameChat(chatId: String, newTitle: String): Boolean =
        withContext(dispatchers.io()) {
            logcat { "DuckAI: RealDuckAiChatStore.renameChat($chatId)" }
            val entity = chatsDao.getById(chatId) ?: return@withContext false
            val updatedJson = runCatching {
                JSONObject(entity.data).put("title", newTitle).toString()
            }.getOrNull() ?: return@withContext false
            chatsDao.upsert(entity.copy(data = updatedJson))
            true
        }

    override suspend fun pinChat(chatId: String) = setPinned(chatId, pinned = true)

    override suspend fun unpinChat(chatId: String) = setPinned(chatId, pinned = false)

    private suspend fun setPinned(chatId: String, pinned: Boolean) {
        withContext(dispatchers.io()) {
            logcat { "DuckAI: RealDuckAiChatStore.setPinned($chatId, $pinned)" }
            val entity = chatsDao.getById(chatId) ?: return@withContext
            val updatedJson = runCatching {
                JSONObject(entity.data).put("pinned", pinned).toString()
            }.getOrNull() ?: return@withContext
            chatsDao.upsert(entity.copy(data = updatedJson))
        }
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
