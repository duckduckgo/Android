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

import androidx.core.net.toUri
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.R
import dev.zacsweers.metro.ContributesIntoSet
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class CloseTabsClientTool @Inject constructor(
    private val feature: AiChatBrowserToolsFeature,
    // Lazy, as in RealNativeInputStateStore: TabDataRepository injects duckchat bindings itself.
    private val tabRepositoryProvider: Lazy<BrowserModeDataProvider<TabRepository>>,
    private val duckChat: DuckChat,
) : ClientTool {

    override val name: String = "closeTabs"
    override val version: Int = 1
    override val permissionMode: ClientToolPermissionMode = ClientToolPermissionMode.Ask(
        promptTitle = R.string.duckAiClientToolCloseTabsPromptTitle,
        promptMessage = R.string.duckAiClientToolCloseTabsPromptMessage,
        name = R.string.duckAiClientToolCloseTabsName,
    )

    override fun isEnabled(): Boolean = feature.self().isEnabled() && feature.closeTabs().isEnabled()

    override fun isValidInput(input: JSONObject): Boolean = parseTabIds(input) != null

    override suspend fun execute(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolResult {
        val requested = parseTabIds(input) ?: return ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS)
        val tabs = tabRepositoryProvider.value.forMode(context.browserMode)
        val tabIds = requested.value ?: run {
            // With no tabIds the target is the chat's own tab, which a Duck.ai tab chat cannot close from inside itself.
            val hostUrl = tabs.getTab(context.chatTabId)?.url
            if (hostUrl != null && duckChat.isDuckChatUrl(hostUrl.toUri())) return ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS)
            listOf(context.chatTabId)
        }

        // Tab IDs are UUIDs, and the output schema only allows those, so any other ID cannot name a tab and is left out.
        val targets = tabIds.distinct().filter { it.isUuid() }
        val closable = targets.filter { tabId -> tabs.getTab(tabId)?.let { duckChat.isToolVisible(it) } == true }
        if (closable.isNotEmpty()) tabs.deleteTabs(closable)

        val results = JSONArray()
        targets.forEach { results.put(JSONObject().put(TAB_ID, it).put(CLOSED, it in closable)) }
        return ClientToolResult.Success(JSONObject().put(TABS, results))
    }

    /** The requested tab IDs, holding null when they are omitted; null itself when the input breaks the schema. */
    private fun parseTabIds(input: JSONObject): Requested? {
        if (!ClientToolInput.hasOnlyKeys(input, TAB_IDS)) return null
        if (!input.has(TAB_IDS)) return Requested(null)
        return ClientToolInput.stringList(input, TAB_IDS, items = 1..MAX_TABS, itemLengths = 0..Int.MAX_VALUE)?.let { Requested(it) }
    }

    private fun String.isUuid(): Boolean = UUID_PATTERN.matches(this)

    private data class Requested(val value: List<String>?)

    private companion object {
        const val TAB_IDS = "tabIds"
        const val TAB_ID = "tabId"
        const val TABS = "tabs"
        const val CLOSED = "closed"
        const val MAX_TABS = 50
        val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
