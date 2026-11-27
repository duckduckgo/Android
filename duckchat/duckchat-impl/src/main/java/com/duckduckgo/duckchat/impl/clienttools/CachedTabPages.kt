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
import com.duckduckgo.duckchat.api.DuckChat
import org.json.JSONObject
import javax.inject.Inject

/**
 * The page context the browser cached for a tab when its page loaded, so background tabs can be read without a live
 * WebView. A tab with no cached context, or one cached for another address, is unavailable.
 */
class CachedTabPages @Inject constructor(
    // Lazy, as in RealNativeInputStateStore: TabDataRepository injects duckchat bindings itself.
    private val tabRepositoryProvider: Lazy<BrowserModeDataProvider<TabRepository>>,
    private val pageContextRepository: TabPageContextRepository,
    private val duckChat: DuckChat,
) {

    sealed interface Lookup {
        data class Found(val page: CachedTabPage) : Lookup

        data class Failure(val code: ClientToolErrorCode) : Lookup
    }

    /** The page in [tabId], or in the chat's own tab when it is null. */
    suspend fun find(
        tabId: String?,
        context: ClientToolCallContext,
    ): Lookup {
        val tab = tabRepositoryProvider.value.forMode(context.browserMode).getTab(tabId ?: context.chatTabId)
            ?: return Lookup.Failure(ClientToolErrorCode.NOT_FOUND)
        if (!duckChat.isToolVisible(tab)) return Lookup.Failure(ClientToolErrorCode.UNAVAILABLE)

        val url = ClientToolOutput.url(tab.url.orEmpty())
        val pageContext = pageContextRepository.getPageContexts(listOf(tab.tabId))[tab.tabId]
            ?.takeIf { ClientToolOutput.url(it.url) == url }
            ?.let { runCatching { JSONObject(it.serializedPageContext) }.getOrNull() }
            ?.takeIf { it.optBoolean(ATTACHABLE, true) }
            ?: return Lookup.Failure(ClientToolErrorCode.UNAVAILABLE)

        val content = pageContext.optString(CONTENT)
        return Lookup.Found(
            CachedTabPage(
                tabId = tab.tabId,
                title = ClientToolOutput.title(pageContext.optString(TITLE).ifBlank { tab.title }),
                url = url,
                content = content,
                extractionTruncated = pageContext.optBoolean(TRUNCATED, false),
                fullContentLength = maxOf(pageContext.optInt(FULL_CONTENT_LENGTH, 0), content.length),
            ),
        )
    }

    private companion object {
        const val TITLE = "title"
        const val CONTENT = "content"
        const val TRUNCATED = "truncated"
        const val FULL_CONTENT_LENGTH = "fullContentLength"
        const val ATTACHABLE = "attachable"
    }
}

/**
 * @param title and [url] already fit the contract.
 * @param extractionTruncated whether the page-context extraction stopped before the end of the page.
 */
data class CachedTabPage(
    val tabId: String,
    val title: String,
    val url: String,
    val content: String,
    val extractionTruncated: Boolean,
    val fullContentLength: Int,
)
