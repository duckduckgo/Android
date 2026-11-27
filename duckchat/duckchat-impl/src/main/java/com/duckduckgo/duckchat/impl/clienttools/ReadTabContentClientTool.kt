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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.R
import dev.zacsweers.metro.ContributesIntoSet
import org.json.JSONObject
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@ContributesIntoSet(AppScope::class)
class ReadTabContentClientTool @Inject constructor(
    private val feature: AiChatBrowserToolsFeature,
    private val cachedTabPages: CachedTabPages,
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
        val page = when (val lookup = cachedTabPages.find(request.tabId, context)) {
            is CachedTabPages.Lookup.Failure -> return ClientToolResult.Failure(lookup.code)
            is CachedTabPages.Lookup.Found -> lookup.page
        }
        val content = ClientToolOutput.truncate(page.content, MAX_CONTENT_LENGTH)
        return ClientToolResult.Success(
            JSONObject()
                .put(TAB_ID, page.tabId)
                .put(TITLE, page.title)
                .put(URL, page.url)
                .put(CONTENT, content)
                .put(TRUNCATED, page.extractionTruncated || content.length < page.content.length)
                .put(FULL_CONTENT_LENGTH, page.fullContentLength)
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
