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

package com.duckduckgo.app.fire

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import javax.inject.Inject

/** Closes Duck.ai tabs left pointing at cleared chat URLs. Never touches non-Duck.ai tabs. */
@ContributesMultibinding(AppScope::class)
class DuckAiTabsCleanupPlugin @Inject constructor(
    private val tabRepository: TabRepository,
    private val duckChat: DuckChat,
) : DataClearingPlugin {

    override suspend fun onClearData(types: Set<ClearableData>) {
        types.forEach { type ->
            when (type) {
                is ClearableData.DuckChats.All -> closeAllDuckAiTabs()
                is ClearableData.DuckChats.Selected -> closeTabsMatching(type.chatUrls)
                else -> { /* not handled by this plugin */ }
            }
        }
    }

    private suspend fun closeAllDuckAiTabs() {
        val ids = tabRepository.getTabs()
            .filter { tab -> tab.url?.toUri()?.let(duckChat::isDuckChatUrl) == true }
            .map { it.tabId }
        if (ids.isNotEmpty()) {
            logcat { "Closing ${ids.size} open Duck.ai tab(s) after chat clear" }
            tabRepository.deleteTabs(ids)
        }
    }

    /**
     * Match by `chatID` query param rather than full URL equality — tabs drift (server redirects,
     * extra query params accumulated during the session) so multiple tabs of the same chat would
     * otherwise miss the match.
     */
    private suspend fun closeTabsMatching(chatUrls: Set<String>) {
        if (chatUrls.isEmpty()) return
        val targetChatIds = chatUrls.mapNotNullTo(mutableSetOf()) { it.toUri().chatIdOrNull() }
        if (targetChatIds.isEmpty()) return
        val ids = tabRepository.getTabs()
            .filter { tab -> tab.url?.toUri()?.chatIdOrNull() in targetChatIds }
            .map { it.tabId }
        if (ids.isNotEmpty()) {
            logcat { "Closing ${ids.size} Duck.ai tab(s) matching the cleared subset" }
            tabRepository.deleteTabs(ids)
        }
    }

    private fun Uri.chatIdOrNull(): String? {
        if (!duckChat.isDuckChatUrl(this)) return null
        return getQueryParameter("chatID")?.takeIf { it.isNotBlank() }
    }
}
