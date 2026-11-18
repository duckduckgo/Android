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

import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.contextual.ContextualSheetCloseRequests
import dev.zacsweers.metro.ContributesIntoSet
import org.json.JSONObject
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class SwitchToTabClientTool @Inject constructor(
    private val feature: AiChatBrowserToolsFeature,
    // Lazy, as in RealNativeInputStateStore: TabDataRepository injects duckchat bindings itself.
    private val tabRepositoryProvider: Lazy<BrowserModeDataProvider<TabRepository>>,
    private val duckChat: DuckChat,
    private val sheetCloseRequests: ContextualSheetCloseRequests,
) : ClientTool {

    override val name: String = "switchToTab"
    override val version: Int = 1
    override val permissionMode: ClientToolPermissionMode = ClientToolPermissionMode.Auto

    override val settingsListing = ClientToolSettingsListing(
        section = ClientToolSettingsSection.DO,
        name = R.string.duckAiClientToolSwitchToTabName,
        description = R.string.duckAiClientToolSwitchToTabDescription,
    )

    override fun isEnabled(): Boolean = feature.self().isEnabled() && feature.switchToTab().isEnabled()

    override fun isValidInput(input: JSONObject): Boolean = parseTabId(input) != null

    override suspend fun execute(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolResult {
        val tabId = parseTabId(input) ?: return ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS)
        val tabs = tabRepositoryProvider.value.forMode(context.browserMode)
        val tab = tabs.getTab(tabId)?.takeIf { duckChat.isToolVisible(it) } ?: return ClientToolResult.Failure(ClientToolErrorCode.NOT_FOUND)
        // A contextual chat lives on its host tab, and on mobile it closes when the user is taken elsewhere.
        if (tab.tabId != context.chatTabId) sheetCloseRequests.close(context.chatTabId)
        tabs.select(tab.tabId)
        return ClientToolResult.Success(JSONObject().put(TAB_ID, tab.tabId))
    }

    private fun parseTabId(input: JSONObject): String? {
        if (!ClientToolInput.hasOnlyKeys(input, TAB_ID)) return null
        return ClientToolInput.string(input, TAB_ID)
    }

    private companion object {
        const val TAB_ID = "tabId"
    }
}
