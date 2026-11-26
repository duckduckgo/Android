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

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.FolderTreeItem
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSitesNames.BOOKMARKS_ROOT
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class BookmarkTabClientToolTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(AiChatBrowserToolsFeature::class.java)
    private val regularTabs: TabRepository = mock()
    private val tabRepositoryProvider = object : BrowserModeDataProvider<TabRepository> {
        override fun forMode(mode: BrowserMode): TabRepository = regularTabs
    }
    private val duckChat: DuckChat = mock {
        on { isDuckChatUrl(any()) } doReturn false
    }
    private val faviconManager: FaviconManager = mock()

    // Folder ID to its children, in order.
    private val tree = mutableMapOf<String, MutableList<FolderTreeItem>>()
    private val savedSites: SavedSitesRepository = mock {
        on { getBookmark(any()) } doAnswer { call ->
            tree.values.flatten().firstOrNull { it.url == call.getArgument<String>(0) }
                ?.let { Bookmark(it.id, it.name, it.url!!, it.parentId, lastModified = null) }
        }
        on { getFolderTreeItems(any()) } doAnswer { tree[it.getArgument<String>(0)].orEmpty().toList() }
        on { insert(any<BookmarkFolder>()) } doAnswer {
            val folder = it.getArgument<BookmarkFolder>(0)
            tree.getOrPut(folder.parentId) { mutableListOf() } += FolderTreeItem(folder.id, folder.name, folder.parentId, null)
            folder
        }
        on { insert(any<SavedSite>()) } doAnswer {
            val bookmark = it.getArgument<Bookmark>(0)
            tree.getOrPut(bookmark.parentId) { mutableListOf() } += FolderTreeItem(bookmark.id, bookmark.title, bookmark.parentId, bookmark.url)
            bookmark
        }
    }

    private val testee = BookmarkTabClientTool(
        feature = feature,
        tabRepositoryProvider = lazy { tabRepositoryProvider },
        duckChat = duckChat,
        savedSitesRepository = savedSites,
        faviconManager = lazy { faviconManager },
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    private val regular =
        ClientToolCallContext(chatTabId = CHAT_TAB, browserMode = BrowserMode.REGULAR, chatId = "chat-1")

    @Test
    fun whenParentAndToolFlagsEnabledThenToolIsEnabled() {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        assertFalse(testee.isEnabled())

        feature.bookmarkTab().setRawStoredState(Toggle.State(enable = true))
        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenToolIsAskedThenItAsksEveryTime() {
        assertTrue(testee.permissionMode is ClientToolPermissionMode.AskEveryTime)
    }

    @Test
    fun whenInputMatchesSchemaThenValid() {
        listOf(
            """{}""",
            """{"tabId":"$TAB"}""",
            """{"tabId":"$TAB","folder":["Travel","Paris"]}""",
            JSONObject().put("folder", JSONArray(List(10) { "a".repeat(256) })).toString(),
            JSONObject().put("folder", JSONArray(listOf("🦆".repeat(256)))).toString(),
        ).forEach { assertTrue(it, testee.isValidInput(JSONObject(it))) }
    }

    @Test
    fun whenInputBreaksSchemaThenInvalidArguments() = runTest {
        listOf(
            """{"folder":[]}""",
            JSONObject().put("folder", JSONArray(List(11) { "a" })).toString(),
            """{"folder":[""]}""",
            JSONObject().put("folder", JSONArray(listOf("a".repeat(257)))).toString(),
            """{"folder":"Travel/Paris"}""",
            """{"folder":[1]}""",
            """{"tabId":1}""",
            """{"tabIds":["$TAB"]}""",
            """{"tabId":"$TAB","title":"Paris"}""",
        ).forEach { input ->
            assertFalse(input, testee.isValidInput(JSONObject(input)))
            assertEquals(input, ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS), testee.execute(JSONObject(input), allowed(PAGE)))
        }
    }

    @Test
    fun whenTabIdOmittedThenPromptShowsTheHostTabsFullUrl() = runTest {
        givenTab(CHAT_TAB, PAGE)

        assertEquals(ClientToolPromptDetail.Shown(PAGE), testee.promptDetail(JSONObject(), regular))
    }

    @Test
    fun whenTabIsUnknownOrDuckAiThenNotFoundBeforePrompt() = runTest {
        whenever(duckChat.isDuckChatUrl(Uri.parse("https://duck.ai/chat"))).thenReturn(true)
        givenTab(CHAT_TAB, "https://duck.ai/chat")

        assertEquals(ClientToolPromptDetail.Failure(ClientToolErrorCode.NOT_FOUND), testee.promptDetail(JSONObject(), regular))
        assertEquals(ClientToolPromptDetail.Failure(ClientToolErrorCode.NOT_FOUND), testee.promptDetail(JSONObject().put("tabId", TAB), regular))
    }

    @Test
    fun whenTabIsNotAWebPageThenUnavailableBeforePrompt() = runTest {
        listOf("about:blank", null).forEach { url ->
            givenTab(TAB, url)

            assertEquals(
                ClientToolPromptDetail.Failure(ClientToolErrorCode.UNAVAILABLE),
                testee.promptDetail(JSONObject().put("tabId", TAB), regular),
            )
        }
    }

    @Test
    fun whenNoFolderThenBookmarkedAtTheTopLevelWithTheTabTitle() = runTest {
        givenTab(TAB, PAGE, title = "Paris guide")

        val value = success(testee.execute(JSONObject().put("tabId", TAB), allowed(PAGE)))

        assertEquals("""{"tabId":"$TAB","alreadyBookmarked":false,"folderCreated":false}""", value.toString())
        assertEquals(listOf(FolderTreeItem(tree.getValue(BOOKMARKS_ROOT).single().id, "Paris guide", BOOKMARKS_ROOT, PAGE)), tree[BOOKMARKS_ROOT])
        verify(faviconManager).persistCachedFavicon(TAB, PAGE)
    }

    @Test
    fun whenFolderPathIsMissingThenItIsCreatedAndTheBookmarkGoesInside() = runTest {
        givenTab(TAB, PAGE)
        val travelId = givenFolder(BOOKMARKS_ROOT, "Travel")

        val value = success(testee.execute(JSONObject("""{"tabId":"$TAB","folder":["Travel","Paris"]}"""), allowed(PAGE)))

        assertTrue(value.getBoolean("folderCreated"))
        assertFalse(value.getBoolean("alreadyBookmarked"))
        val paris = tree.getValue(travelId).single()
        assertEquals("Paris", paris.name)
        assertEquals(PAGE, tree.getValue(paris.id).single().url)
        assertEquals(1, tree.getValue(BOOKMARKS_ROOT).size)
    }

    @Test
    fun whenPageIsAlreadyInThatFolderThenNothingIsAdded() = runTest {
        givenTab(TAB, PAGE)
        val travelId = givenFolder(BOOKMARKS_ROOT, "Travel")
        tree.getValue(travelId) += FolderTreeItem("b", "Paris", travelId, PAGE)

        val value = success(testee.execute(JSONObject("""{"tabId":"$TAB","folder":["Travel"]}"""), allowed(PAGE)))

        assertEquals("""{"tabId":"$TAB","alreadyBookmarked":true,"folderCreated":false}""", value.toString())
        verify(savedSites, never()).insert(any<SavedSite>())
        verify(faviconManager, never()).persistCachedFavicon(any(), any())
    }

    @Test
    fun whenPageIsBookmarkedInAnotherFolderThenNothingIsAddedOrCreated() = runTest {
        givenTab(TAB, PAGE)
        tree.getOrPut(BOOKMARKS_ROOT) { mutableListOf() } += FolderTreeItem("b", "Paris", BOOKMARKS_ROOT, PAGE)

        val value = success(testee.execute(JSONObject("""{"tabId":"$TAB","folder":["Travel"]}"""), allowed(PAGE)))

        assertEquals("""{"tabId":"$TAB","alreadyBookmarked":true,"folderCreated":false}""", value.toString())
        assertEquals(listOf("b"), tree.values.flatten().map { it.id })
        verify(savedSites, never()).insert(any<SavedSite>())
        verify(savedSites, never()).insert(any<BookmarkFolder>())
        verify(faviconManager, never()).persistCachedFavicon(any(), any())
    }

    @Test
    fun whenTabMovedOnWhilePromptWasOpenThenTheAllowedUrlIsBookmarkedWithoutTheNewTitle() = runTest {
        givenTab(TAB, "https://example.com/other", title = "Other page")

        success(testee.execute(JSONObject().put("tabId", TAB), allowed(PAGE)))

        val bookmark = tree.getValue(BOOKMARKS_ROOT).single()
        assertEquals(PAGE, bookmark.url)
        assertEquals("", bookmark.name)
        verify(faviconManager, never()).persistCachedFavicon(any(), any())
    }

    @Test
    fun whenTabClosedWhilePromptWasOpenThenNotFound() = runTest {
        assertEquals(ClientToolResult.Failure(ClientToolErrorCode.NOT_FOUND), testee.execute(JSONObject().put("tabId", TAB), allowed(PAGE)))
        verify(savedSites, never()).insert(any<SavedSite>())
    }

    @Test
    fun whenRunWithoutAnAllowedUrlThenNothingIsBookmarked() = runTest {
        givenTab(TAB, PAGE)

        assertEquals(ClientToolResult.Failure(ClientToolErrorCode.EXECUTION_FAILED), testee.execute(JSONObject().put("tabId", TAB), regular))
        verify(savedSites, never()).insert(anyOrNull<SavedSite>())
    }

    private fun allowed(url: String) = regular.copy(allowedDetail = url)

    private fun success(result: ClientToolResult): JSONObject = (result as ClientToolResult.Success).value

    private fun givenFolder(
        parentId: String,
        name: String,
    ): String = savedSites.insert(BookmarkFolder(name = name, parentId = parentId)).id.also { tree[it] = mutableListOf() }

    private suspend fun givenTab(
        tabId: String,
        url: String?,
        title: String? = null,
    ) {
        whenever(regularTabs.getTab(tabId)).thenReturn(TabEntity(tabId = tabId, url = url, title = title))
    }

    private companion object {
        const val CHAT_TAB = "11111111-1111-1111-1111-111111111111"
        const val TAB = "6f9619ff-8b86-d011-b42d-00c04fc964ff"
        const val PAGE = "https://user@example.com/paris?day=1#top"
    }
}
