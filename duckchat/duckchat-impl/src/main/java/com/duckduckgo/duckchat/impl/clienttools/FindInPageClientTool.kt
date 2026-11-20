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
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

/** Finds text in the page context cached for a tab, the same text readTabContent reads. */
@ContributesIntoSet(AppScope::class)
class FindInPageClientTool @Inject constructor(
    private val feature: AiChatBrowserToolsFeature,
    private val cachedTabPages: CachedTabPages,
) : ClientTool {

    override val name: String = "findInPage"
    override val version: Int = 1
    override val permissionMode: ClientToolPermissionMode = ClientToolPermissionMode.Ask(
        promptTitle = R.string.duckAiClientToolFindInPagePromptTitle,
        promptMessage = R.string.duckAiClientToolFindInPagePromptMessage,
        name = R.string.duckAiClientToolFindInPageName,
    )

    override val settingsListing = ClientToolSettingsListing(
        section = ClientToolSettingsSection.SEE,
        name = R.string.duckAiClientToolFindInPageName,
        description = R.string.duckAiClientToolFindInPageDescription,
        icon = CommonR.drawable.ic_find_in_page_24,
    )

    override fun isEnabled(): Boolean = feature.self().isEnabled() && feature.findInPage().isEnabled()

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

        val starts = matchStarts(page.content, request.query, request.caseSensitive)
        val matches = JSONArray()
        starts.take(request.maxMatches).forEachIndexed { index, start ->
            matches.put(
                JSONObject()
                    .put(SNIPPET, snippet(page.content, start, request.query.length))
                    .put(MATCH_INDEX, index),
            )
        }
        return ClientToolResult.Success(
            JSONObject()
                .put(TAB_ID, page.tabId)
                .put(TITLE, page.title)
                .put(URL, page.url)
                .put(QUERY, request.query)
                .put(MATCH_COUNT, starts.size)
                .put(MATCHES, matches)
                .put(TRUNCATED, starts.size > request.maxMatches)
                .put(CONTENT_TRUNCATED, page.extractionTruncated)
                // Matches come from the extracted page text, not the live page, so they may not line up with highlighting.
                .put(MATCH_INDEXES_RELIABLE, false),
        )
    }

    /** Where each non-overlapping match of [query] starts in [content]. */
    private fun matchStarts(
        content: String,
        query: String,
        caseSensitive: Boolean,
    ): List<Int> {
        val starts = mutableListOf<Int>()
        var from = 0
        while (true) {
            val start = content.indexOf(query, from, ignoreCase = !caseSensitive)
            if (start < 0) return starts
            starts += start
            from = start + query.length
        }
    }

    /** The match with the text around it, whitespace collapsed, cut to the contract's limit. */
    private fun snippet(
        content: String,
        start: Int,
        length: Int,
    ): String {
        val context = ((MAX_SNIPPET_LENGTH - length) / 2).coerceAtLeast(0)
        val from = (start - context).coerceAtLeast(0)
        val to = (start + length + context).coerceAtMost(content.length)
        val text = content.substring(from, to).replace(WHITESPACE, " ").trim()
        return ClientToolOutput.truncate(text, MAX_SNIPPET_LENGTH)
    }

    private fun parse(input: JSONObject): Request? {
        if (!ClientToolInput.hasOnlyKeys(input, QUERY, TAB_ID, CASE_SENSITIVE, MAX_MATCHES)) return null
        val query = ClientToolInput.string(input, QUERY, lengths = 1..MAX_QUERY_LENGTH) ?: return null
        val tabId = if (input.has(TAB_ID)) ClientToolInput.string(input, TAB_ID) ?: return null else null
        val caseSensitive = if (input.has(CASE_SENSITIVE)) input.opt(CASE_SENSITIVE) as? Boolean ?: return null else false
        val maxMatches = ClientToolInput.limit(input, MAX_MATCHES, default = DEFAULT_MAX_MATCHES, max = MAX_MAX_MATCHES) ?: return null
        return Request(query, tabId, caseSensitive, maxMatches)
    }

    private data class Request(
        val query: String,
        val tabId: String?,
        val caseSensitive: Boolean,
        val maxMatches: Int,
    )

    private companion object {
        const val QUERY = "query"
        const val TAB_ID = "tabId"
        const val CASE_SENSITIVE = "caseSensitive"
        const val MAX_MATCHES = "maxMatches"
        const val TITLE = "title"
        const val URL = "url"
        const val MATCH_COUNT = "matchCount"
        const val MATCHES = "matches"
        const val SNIPPET = "snippet"
        const val MATCH_INDEX = "matchIndex"
        const val TRUNCATED = "truncated"
        const val CONTENT_TRUNCATED = "contentTruncated"
        const val MATCH_INDEXES_RELIABLE = "matchIndexesReliable"
        const val DEFAULT_MAX_MATCHES = 10
        const val MAX_MAX_MATCHES = 50
        const val MAX_QUERY_LENGTH = 1000
        const val MAX_SNIPPET_LENGTH = 100
        val WHITESPACE = Regex("\\s+")
    }
}
