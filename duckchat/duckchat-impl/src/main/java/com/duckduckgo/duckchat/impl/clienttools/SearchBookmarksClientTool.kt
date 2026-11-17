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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.FolderTreeItem
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class SearchBookmarksClientTool @Inject constructor(
    private val feature: AiChatBrowserToolsFeature,
    private val savedSitesRepository: SavedSitesRepository,
    private val dispatchers: DispatcherProvider,
) : ClientTool {

    override val name: String = "searchBookmarks"
    override val version: Int = 1
    override val permissionMode: ClientToolPermissionMode = ClientToolPermissionMode.Ask(
        promptTitle = R.string.duckAiClientToolSearchBookmarksPromptTitle,
        promptMessage = R.string.duckAiClientToolSearchBookmarksPromptMessage,
        name = R.string.duckAiClientToolSearchBookmarksName,
        description = R.string.duckAiClientToolSearchBookmarksDescription,
    )

    override fun isEnabled(): Boolean = feature.self().isEnabled() && feature.searchBookmarks().isEnabled()

    override fun isValidInput(input: JSONObject): Boolean = parse(input) != null

    override suspend fun execute(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolResult {
        val request = parse(input) ?: return ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS)
        val results = JSONArray()
        withContext(dispatchers.io()) { search(request) }.forEach { found ->
            results.put(
                JSONObject()
                    .put(TITLE, ClientToolOutput.title(found.title))
                    .put(URL, found.url)
                    .put(FOLDER, JSONArray(found.folder.map { ClientToolOutput.truncate(it, MAX_FOLDER_NAME_LENGTH) })),
            )
        }
        return ClientToolResult.Success(JSONObject().put(RESULTS, results))
    }

    private fun search(request: Request): List<Found> {
        val start = findFolder(request.folder.orEmpty()) ?: return emptyList()
        val query = request.query?.lowercase(Locale.ROOT)
        val found = mutableListOf<Found>()

        fun visit(
            folderId: String,
            path: List<String>,
        ) {
            savedSitesRepository.getFolderTreeItems(folderId).forEach { item ->
                if (found.size == request.limit) return
                val url = item.url
                if (url == null) {
                    // The output carries at most this many folder names, so deeper bookmarks cannot be reported.
                    if (path.size < MAX_FOLDER_DEPTH) visit(item.id, path + item.name)
                } else {
                    // Matched on the URL as reported, so a query can't probe the parts the output drops.
                    val shownUrl = ClientToolOutput.url(url)
                    if (query == null || (listOf(item.name, shownUrl) + path).any { it.lowercase(Locale.ROOT).contains(query) }) {
                        found += Found(item.name, shownUrl, path)
                    }
                }
            }
        }
        visit(start, request.folder.orEmpty())
        return found
    }

    /** The ID of the folder at [path] from the top of Bookmarks, or null when one of its folders doesn't exist. */
    private fun findFolder(path: List<String>): String? =
        path.fold<String, String?>(SavedSitesNames.BOOKMARKS_ROOT) { parentId, name ->
            parentId?.let { id -> savedSitesRepository.getFolderTreeItems(id).firstOrNull { it.isFolder() && it.name == name }?.id }
        }

    private fun FolderTreeItem.isFolder(): Boolean = url == null

    private fun parse(input: JSONObject): Request? {
        if (!ClientToolInput.hasOnlyKeys(input, QUERY, FOLDER, LIMIT)) return null
        val query = if (input.has(QUERY)) ClientToolInput.string(input, QUERY, lengths = 0..MAX_QUERY_LENGTH) ?: return null else null
        val folder = if (input.has(FOLDER)) {
            ClientToolInput.stringList(input, FOLDER, items = 1..MAX_FOLDER_DEPTH, itemLengths = 1..MAX_FOLDER_NAME_LENGTH) ?: return null
        } else {
            null
        }
        val limit = ClientToolInput.limit(input, LIMIT, default = DEFAULT_LIMIT, max = MAX_LIMIT) ?: return null
        return Request(query, folder, limit)
    }

    private data class Request(
        val query: String?,
        val folder: List<String>?,
        val limit: Int,
    )

    private data class Found(
        val title: String,
        val url: String,
        val folder: List<String>,
    )

    private companion object {
        const val QUERY = "query"
        const val FOLDER = "folder"
        const val LIMIT = "limit"
        const val RESULTS = "results"
        const val TITLE = "title"
        const val URL = "url"
        const val DEFAULT_LIMIT = 10
        const val MAX_LIMIT = 50
        const val MAX_QUERY_LENGTH = 1000
        const val MAX_FOLDER_DEPTH = 10
        const val MAX_FOLDER_NAME_LENGTH = 256
    }
}
