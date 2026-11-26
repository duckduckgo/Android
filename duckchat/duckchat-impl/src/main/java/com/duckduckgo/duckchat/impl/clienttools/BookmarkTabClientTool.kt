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
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class BookmarkTabClientTool @Inject constructor(
    private val feature: AiChatBrowserToolsFeature,
    // Lazy, as in RealNativeInputStateStore: TabDataRepository injects duckchat bindings itself.
    private val tabRepositoryProvider: Lazy<BrowserModeDataProvider<TabRepository>>,
    private val duckChat: DuckChat,
    private val savedSitesRepository: SavedSitesRepository,
    private val faviconManager: Lazy<FaviconManager>,
    private val dispatchers: DispatcherProvider,
) : ClientTool {

    override val name: String = "bookmarkTab"
    override val version: Int = 1
    override val permissionMode: ClientToolPermissionMode = ClientToolPermissionMode.AskEveryTime(
        promptTitle = R.string.duckAiClientToolBookmarkTabPromptTitle,
        promptMessage = R.string.duckAiClientToolBookmarkTabPromptMessage,
    )

    override fun isEnabled(): Boolean = feature.self().isEnabled() && feature.bookmarkTab().isEnabled()

    override fun isValidInput(input: JSONObject): Boolean = parse(input) != null

    override suspend fun promptDetail(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolPromptDetail {
        val request = parse(input) ?: return ClientToolPromptDetail.Failure(ClientToolErrorCode.INVALID_ARGUMENTS)
        return when (val target = target(request.tabId ?: context.chatTabId, context)) {
            is Target.Page -> ClientToolPromptDetail.Shown(target.url)
            is Target.Missing -> ClientToolPromptDetail.Failure(target.code)
        }
    }

    override suspend fun execute(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolResult {
        val request = parse(input) ?: return ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS)
        val url = context.allowedDetail ?: return ClientToolResult.Failure(ClientToolErrorCode.EXECUTION_FAILED)
        // Looked up again after the prompt: the tab may have closed or moved on while it was open.
        val target = when (val now = target(request.tabId ?: context.chatTabId, context)) {
            is Target.Page -> now
            is Target.Missing -> return ClientToolResult.Failure(now.code)
        }
        // The user allowed this URL; the tab's title only describes it while the tab still shows it.
        val title = if (target.url == url) target.tab.title.orEmpty() else ""
        val saved = withContext(dispatchers.io()) { save(url, title, request.folder.orEmpty()) }
        if (!saved.alreadyBookmarked && target.url == url) faviconManager.value.persistCachedFavicon(target.tab.tabId, url)
        return ClientToolResult.Success(
            JSONObject()
                .put(TAB_ID, target.tab.tabId)
                .put(ALREADY_BOOKMARKED, saved.alreadyBookmarked)
                .put(FOLDER_CREATED, saved.folderCreated),
        )
    }

    private fun save(
        url: String,
        title: String,
        folderPath: List<String>,
    ): Saved {
        // Matches the browser's Add Bookmark: one bookmark per URL, and no folders made for a page already saved.
        if (savedSitesRepository.getBookmark(url) != null) return Saved(alreadyBookmarked = true, folderCreated = false)

        var folderId = SavedSitesNames.BOOKMARKS_ROOT
        var folderCreated = false
        folderPath.forEach { name ->
            val existing = savedSitesRepository.getFolderTreeItems(folderId).firstOrNull { it.url == null && it.name == name }
            folderId = existing?.id ?: savedSitesRepository.insert(BookmarkFolder(name = name, parentId = folderId)).id.also { folderCreated = true }
        }
        savedSitesRepository.insert(
            Bookmark(
                id = UUID.randomUUID().toString(),
                title = title,
                url = url,
                parentId = folderId,
                lastModified = DatabaseDateFormatter.iso8601(),
            ),
        )
        return Saved(alreadyBookmarked = false, folderCreated)
    }

    private suspend fun target(
        tabId: String,
        context: ClientToolCallContext,
    ): Target {
        val tab = tabRepositoryProvider.value.forMode(context.browserMode).getTab(tabId) ?: return Target.Missing(ClientToolErrorCode.NOT_FOUND)
        val url = tab.url
        return when {
            url != null && duckChat.isDuckChatUrl(url.toUri()) -> Target.Missing(ClientToolErrorCode.NOT_FOUND)
            url == null || !url.isHttpOrHttps() -> Target.Missing(ClientToolErrorCode.UNAVAILABLE)
            else -> Target.Page(tab, url)
        }
    }

    private fun parse(input: JSONObject): Request? {
        if (!ClientToolInput.hasOnlyKeys(input, TAB_ID, FOLDER)) return null
        val tabId = if (input.has(TAB_ID)) ClientToolInput.string(input, TAB_ID) ?: return null else null
        val folder = if (input.has(FOLDER)) {
            ClientToolInput.stringList(input, FOLDER, items = 1..MAX_FOLDER_DEPTH, itemLengths = 1..MAX_FOLDER_NAME_LENGTH) ?: return null
        } else {
            null
        }
        return Request(tabId, folder)
    }

    private data class Request(
        val tabId: String?,
        val folder: List<String>?,
    )

    private sealed interface Target {
        data class Page(
            val tab: TabEntity,
            val url: String,
        ) : Target

        data class Missing(val code: ClientToolErrorCode) : Target
    }

    private data class Saved(
        val alreadyBookmarked: Boolean,
        val folderCreated: Boolean,
    )

    private companion object {
        const val TAB_ID = "tabId"
        const val FOLDER = "folder"
        const val ALREADY_BOOKMARKED = "alreadyBookmarked"
        const val FOLDER_CREATED = "folderCreated"
        const val MAX_FOLDER_DEPTH = 10
        const val MAX_FOLDER_NAME_LENGTH = 256
    }
}
