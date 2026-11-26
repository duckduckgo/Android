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

package com.duckduckgo.duckchat.impl.clienttools

import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

/**
 * Tools the user allowed for one Duck.ai chat, and how many single-tool prompts it has shown.
 * Kept in memory only, so an app restart ends every grant.
 */
@SingleIn(AppScope::class)
class ClientToolChatGrants @Inject constructor() {

    private val toolsByChat = mutableMapOf<String, MutableSet<String>>()
    private val promptsByChat = mutableMapOf<String, Int>()

    fun isGranted(
        chatId: String,
        toolName: String,
    ): Boolean = synchronized(toolsByChat) { toolsByChat[chatId]?.contains(toolName) == true }

    fun grant(
        chatId: String,
        toolNames: Collection<String>,
    ) {
        synchronized(toolsByChat) { toolsByChat.getOrPut(chatId) { mutableSetOf() }.addAll(toolNames) }
    }

    /** Counts a single-tool prompt shown in [chatId], and returns its number in the chat, starting at 1. */
    fun countPrompt(chatId: String): Int = synchronized(toolsByChat) {
        (promptsByChat.getOrDefault(chatId, 0) + 1).also { promptsByChat[chatId] = it }
    }

    fun revoke(toolName: String) {
        synchronized(toolsByChat) { toolsByChat.values.forEach { it.remove(toolName) } }
    }

    fun revokeAll() {
        synchronized(toolsByChat) { toolsByChat.clear() }
    }

    /** Forgets every chat, as the Fire Button does. */
    fun clear() {
        synchronized(toolsByChat) {
            toolsByChat.clear()
            promptsByChat.clear()
        }
    }
}

/** The Fire Button ends every chat grant. */
@ContributesIntoSet(AppScope::class)
class ClientToolChatGrantsClearingPlugin @Inject constructor(
    private val grants: ClientToolChatGrants,
) : DataClearingPlugin {

    override suspend fun onClearData(types: Set<ClearableData>) {
        if (types.any { it.isWholeClear() }) grants.clear()
    }

    // Single tab and selected chat clears leave other chats, and their grants, in place.
    private fun ClearableData.isWholeClear(): Boolean = when (this) {
        ClearableData.BrowserData.All, is ClearableData.BrowserData.AllForMode -> true
        ClearableData.Tabs.All, is ClearableData.Tabs.AllForMode -> true
        ClearableData.DuckChats.All, is ClearableData.DuckChats.AllForMode -> true
        is ClearableData.BrowserData.SingleForMode, is ClearableData.Tabs.SingleForMode, is ClearableData.DuckChats.SelectedForMode -> false
    }
}
