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
import com.duckduckgo.downloads.api.DownloadsRepository
import com.duckduckgo.duckchat.impl.R
import dev.zacsweers.metro.ContributesIntoSet
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class SearchDownloadsClientTool @Inject constructor(
    private val feature: AiChatBrowserToolsFeature,
    private val downloadsRepository: DownloadsRepository,
) : ClientTool {

    override val name: String = "searchDownloads"
    override val version: Int = 1
    override val permissionMode: ClientToolPermissionMode = ClientToolPermissionMode.Ask(
        promptTitle = R.string.duckAiClientToolSearchDownloadsPromptTitle,
        promptMessage = R.string.duckAiClientToolSearchDownloadsPromptMessage,
        name = R.string.duckAiClientToolSearchDownloadsName,
    )

    override fun isEnabled(): Boolean = feature.self().isEnabled() && feature.searchDownloads().isEnabled()

    override fun isValidInput(input: JSONObject): Boolean = parse(input) != null

    override suspend fun execute(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolResult {
        val request = parse(input) ?: return ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS)
        val query = request.query.lowercase(Locale.ROOT)
        val results = JSONArray()
        downloadsRepository.getDownloads()
            .filter { it.fileName.lowercase(Locale.ROOT).contains(query) }
            .mapNotNull { download -> downloadedAt(download.createdAt)?.let { download.fileName to it } }
            .sortedByDescending { (_, downloadedAt) -> downloadedAt }
            .take(request.limit)
            .forEach { (fileName, downloadedAt) ->
                results.put(
                    JSONObject()
                        .put(FILE_NAME, ClientToolOutput.truncate(fileName, MAX_FILE_NAME_LENGTH))
                        // Downloads don't keep their source URL on Android, and the contract allows it empty.
                        .put(URL, "")
                        .put(DOWNLOADED_AT, UTC_TIMESTAMP.format(downloadedAt)),
                )
            }
        return ClientToolResult.Success(JSONObject().put(RESULTS, results))
    }

    /** createdAt is stored as device-local time without an offset, by DatabaseDateFormatter.timestamp. */
    private fun downloadedAt(createdAt: String): Instant? = try {
        LocalDateTime.parse(createdAt).atZone(ZoneId.systemDefault()).toInstant()
    } catch (e: DateTimeParseException) {
        null
    }

    private fun parse(input: JSONObject): Request? {
        if (!ClientToolInput.hasOnlyKeys(input, QUERY, LIMIT)) return null
        val query = ClientToolInput.string(input, QUERY, lengths = 1..MAX_QUERY_LENGTH) ?: return null
        val limit = ClientToolInput.limit(input, LIMIT, default = DEFAULT_LIMIT, max = MAX_LIMIT) ?: return null
        return Request(query, limit)
    }

    private data class Request(
        val query: String,
        val limit: Int,
    )

    private companion object {
        const val QUERY = "query"
        const val LIMIT = "limit"
        const val RESULTS = "results"
        const val FILE_NAME = "fileName"
        const val URL = "url"
        const val DOWNLOADED_AT = "downloadedAt"
        const val DEFAULT_LIMIT = 10
        const val MAX_LIMIT = 50
        const val MAX_QUERY_LENGTH = 1000
        const val MAX_FILE_NAME_LENGTH = 256
        val UTC_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    }
}
