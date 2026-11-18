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
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.contextual.ContextualSheetCloseRequests
import dev.zacsweers.metro.ContributesIntoSet
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class OpenTabClientTool @Inject constructor(
    private val feature: AiChatBrowserToolsFeature,
    // Lazy, as in RealNativeInputStateStore: TabDataRepository injects duckchat bindings itself.
    private val tabRepositoryProvider: Lazy<BrowserModeDataProvider<TabRepository>>,
    private val sheetCloseRequests: ContextualSheetCloseRequests,
) : ClientTool {

    override val name: String = "openTab"
    override val version: Int = 1
    override val permissionMode: ClientToolPermissionMode = ClientToolPermissionMode.AskEveryTime(
        promptTitle = R.string.duckAiClientToolOpenTabPromptTitle,
        promptMessage = R.string.duckAiClientToolOpenTabPromptMessage,
    )

    override fun isEnabled(): Boolean = feature.self().isEnabled() && feature.openTab().isEnabled()

    // A schema-valid URL with another scheme is still invalid_arguments, and fails before the prompt.
    override fun isValidInput(input: JSONObject): Boolean = parseUrl(input) != null

    override suspend fun promptDetail(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolPromptDetail = parseUrl(input)?.let { ClientToolPromptDetail.Shown(it) }
        ?: ClientToolPromptDetail.Failure(ClientToolErrorCode.INVALID_ARGUMENTS)

    override suspend fun execute(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolResult {
        val url = context.allowedDetail ?: return ClientToolResult.Failure(ClientToolErrorCode.EXECUTION_FAILED)
        // The new tab takes the user away from a contextual chat, as switchToTab does.
        sheetCloseRequests.close(context.chatTabId)
        val tabId = tabRepositoryProvider.value.forMode(context.browserMode).add(url = url, skipHome = true)
        return ClientToolResult.Success(JSONObject().put(TAB_ID, tabId))
    }

    private fun parseUrl(input: JSONObject): String? {
        if (!ClientToolInput.hasOnlyKeys(input, URL)) return null
        val url = ClientToolInput.string(input, URL, lengths = 1..MAX_URL_LENGTH) ?: return null
        return url.takeIf { it.toHttpUrlOrNull() != null }
    }

    private companion object {
        const val URL = "url"
        const val TAB_ID = "tabId"
        const val MAX_URL_LENGTH = 1280
    }
}
