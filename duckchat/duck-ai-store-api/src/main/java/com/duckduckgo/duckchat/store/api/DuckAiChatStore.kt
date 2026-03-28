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

package com.duckduckgo.duckchat.store.api

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

    /** Deletes the chat with [chatId] and its associated files. Returns true if the chat existed, false if not found. */
    suspend fun deleteChat(chatId: String): Boolean
}
