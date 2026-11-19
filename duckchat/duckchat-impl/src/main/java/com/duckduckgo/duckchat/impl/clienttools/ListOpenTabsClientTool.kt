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
import dev.zacsweers.metro.ContributesIntoSet
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class ListOpenTabsClientTool @Inject constructor(
    private val feature: AiChatBrowserToolsFeature,
    // Lazy, as in RealNativeInputStateStore: TabDataRepository injects duckchat bindings itself.
    private val tabRepositoryProvider: Lazy<BrowserModeDataProvider<TabRepository>>,
    private val duckChat: DuckChat,
    private val tabReadability: TabReadability,
) : ClientTool {

    override val name: String = "listOpenTabs"
    override val version: Int = 1
    override val permissionMode: ClientToolPermissionMode = ClientToolPermissionMode.ASK

    override fun isEnabled(): Boolean = feature.self().isEnabled() && feature.listOpenTabs().isEnabled()

    override fun isValidInput(input: JSONObject): Boolean = parseLimit(input) != null

    override suspend fun execute(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolResult {
        val limit = parseLimit(input) ?: return ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS)

        val tabs = JSONArray()
        tabRepositoryProvider.value.forMode(context.browserMode).getTabs()
            .asSequence()
            .filter { tab -> tab.url.let { it != null && it.isHttpOrHttps() && !duckChat.isDuckChatUrl(it.toUri()) } }
            .take(limit)
            .forEach { tab ->
                tabs.put(
                    JSONObject()
                        .put("tabId", tab.tabId)
                        .put("title", ClientToolOutput.title(tab.title))
                        .put("url", ClientToolOutput.url(tab.url.orEmpty()))
                        .put("isCurrentTab", tab.tabId == context.chatTabId)
                        .put("isAttachable", tabReadability.canRead(tab)),
                )
            }
        return ClientToolResult.Success(JSONObject().put("tabs", tabs))
    }

    private fun parseLimit(input: JSONObject): Int? {
        if (input.keys().asSequence().any { it != LIMIT }) return null
        val limit = input.opt(LIMIT) ?: return MAX_LIMIT
        return (limit as? Number)?.toWholeLong()?.takeIf { it in 1..MAX_LIMIT }?.toInt()
    }

    private companion object {
        const val LIMIT = "limit"
        const val MAX_LIMIT = 50
    }
}
