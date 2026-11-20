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

import com.duckduckgo.app.tabs.model.TabPageContextRepository
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.R
import dev.zacsweers.metro.ContributesIntoSet
import org.json.JSONObject
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

/**
 * Reads the page context the browser cached for a tab when its page loaded, so background tabs can be read
 * without a live WebView. A tab with no cached context, or one cached for another address, is unavailable.
 */
@ContributesIntoSet(AppScope::class)
class ReadTabContentClientTool @Inject constructor(
    private val feature: AiChatBrowserToolsFeature,
    // Lazy, as in RealNativeInputStateStore: TabDataRepository injects duckchat bindings itself.
    private val tabRepositoryProvider: Lazy<BrowserModeDataProvider<TabRepository>>,
    private val pageContextRepository: TabPageContextRepository,
    private val duckChat: DuckChat,
) : ClientTool {

    override val name: String = "readTabContent"
    override val version: Int = 1
    override val permissionMode: ClientToolPermissionMode = ClientToolPermissionMode.Ask(
        promptTitle = R.string.duckAiClientToolReadTabContentPromptTitle,
        promptMessage = R.string.duckAiClientToolReadTabContentPromptMessage,
        name = R.string.duckAiClientToolReadTabContentName,
    )

    override val settingsListing = ClientToolSettingsListing(
        section = ClientToolSettingsSection.SEE,
        name = R.string.duckAiClientToolReadTabContentName,
        description = R.string.duckAiClientToolReadTabContentDescription,
        icon = CommonR.drawable.ic_eye_24,
    )

    override fun isEnabled(): Boolean = feature.self().isEnabled() && feature.readTabContent().isEnabled()

    override fun isValidInput(input: JSONObject): Boolean = parse(input) != null

    override suspend fun execute(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolResult {
        val request = parse(input) ?: return ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS)
        val tabId = request.tabId ?: context.chatTabId
        val tab = tabRepositoryProvider.value.forMode(context.browserMode).getTab(tabId)
            ?: return ClientToolResult.Failure(ClientToolErrorCode.NOT_FOUND)
        if (!duckChat.isToolVisible(tab)) return ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE)

        val url = ClientToolOutput.url(tab.url.orEmpty())
        val pageContext = pageContextRepository.getPageContexts(listOf(tab.tabId))[tab.tabId]
            ?.takeIf { ClientToolOutput.url(it.url) == url }
            ?.let { runCatching { JSONObject(it.serializedPageContext) }.getOrNull() }
            ?.takeIf { it.optBoolean(ATTACHABLE, true) }
            ?: return ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE)

        val fullContent = pageContext.optString(CONTENT)
        val content = ClientToolOutput.truncate(fullContent, MAX_CONTENT_LENGTH)
        val fullContentLength = maxOf(pageContext.optInt(FULL_CONTENT_LENGTH, 0), fullContent.length)
        return ClientToolResult.Success(
            JSONObject()
                .put(TAB_ID, tab.tabId)
                .put(TITLE, ClientToolOutput.title(pageContext.optString(TITLE).ifBlank { tab.title }))
                .put(URL, url)
                .put(CONTENT, content)
                .put(TRUNCATED, pageContext.optBoolean(TRUNCATED, false) || content.length < fullContent.length)
                .put(FULL_CONTENT_LENGTH, fullContentLength)
                .put(ATTACHABLE, true)
                // Reading a tab never attaches it to the chat.
                .put(ATTACHED, false),
        )
    }

    private fun parse(input: JSONObject): Request? {
        if (!ClientToolInput.hasOnlyKeys(input, TAB_ID)) return null
        val tabId = if (input.has(TAB_ID)) ClientToolInput.string(input, TAB_ID) ?: return null else null
        return Request(tabId)
    }

    private data class Request(val tabId: String?)

    private companion object {
        const val TAB_ID = "tabId"
        const val TITLE = "title"
        const val URL = "url"
        const val CONTENT = "content"
        const val TRUNCATED = "truncated"
        const val FULL_CONTENT_LENGTH = "fullContentLength"
        const val ATTACHABLE = "attachable"
        const val ATTACHED = "attached"
        const val MAX_CONTENT_LENGTH = 3000
    }
}
