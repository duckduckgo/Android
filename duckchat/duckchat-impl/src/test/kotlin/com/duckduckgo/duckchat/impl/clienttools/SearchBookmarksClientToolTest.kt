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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.FolderTreeItem
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class SearchBookmarksClientToolTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(AiChatBrowserToolsFeature::class.java)
    private val tree = mutableMapOf<String, MutableList<FolderTreeItem>>()
    private val savedSites: SavedSitesRepository = mock {
        on { getFolderTreeItems(any()) } doAnswer { tree[it.getArgument<String>(0)].orEmpty().toList() }
    }
    private var nextId = 0

    private val testee = SearchBookmarksClientTool(feature, savedSites, coroutineRule.testDispatcherProvider)

    private val context = ClientToolCallContext(chatTabId = "chat", browserMode = BrowserMode.REGULAR, chatId = "chat-1")

    @Test
    fun whenParentAndToolFlagsEnabledThenToolIsEnabled() {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        assertFalse(testee.isEnabled())

        feature.searchBookmarks().setRawStoredState(Toggle.State(enable = true))
        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenInputMatchesSchemaThenValid() {
        listOf(
            """{}""",
            """{"query":"disney"}""",
            """{"folder":["Travel","Disney"],"limit":50}""",
            """{"query":""}""",
            JSONObject().put("query", "🦆".repeat(1000)).toString(),
            JSONObject().put("folder", JSONArray(List(10) { "a".repeat(256) })).toString(),
        ).forEach { assertTrue(it, testee.isValidInput(JSONObject(it))) }
    }

    @Test
    fun whenInputBreaksSchemaThenInvalidArguments() = runTest {
        listOf(
            JSONObject().put("query", "a".repeat(1001)).toString(),
            """{"query":1}""",
            """{"folder":[]}""",
            JSONObject().put("folder", JSONArray(List(11) { "a" })).toString(),
            """{"folder":[""]}""",
            JSONObject().put("folder", JSONArray(listOf("a".repeat(257)))).toString(),
            """{"folder":"Travel/Disney"}""",
            """{"limit":51}""",
            """{"limit":0}""",
            """{"limit":1.5}""",
            """{"query":"disney","recursive":false}""",
        ).forEach { input ->
            assertFalse(input, testee.isValidInput(JSONObject(input)))
            assertEquals(input, ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS), testee.execute(JSONObject(input), context))
        }
    }

    @Test
    fun whenNoQueryThenBookmarksAreListedInOrderWithTheirFolderPath() = runTest {
        bookmark(BOOKMARKS_ROOT, "Top", "https://example.com/top")
        val travel = folder(BOOKMARKS_ROOT, "Travel")
        bookmark(travel, "Tickets", "https://example.com/disney/tickets?code=secret#x")
        bookmark(BOOKMARKS_ROOT, "Last", "https://example.com/last")

        val value = search("""{}""")

        assertEquals(
            """{"results":[""" +
                """{"title":"Top","url":"https:\/\/example.com\/top","folder":[]},""" +
                """{"title":"Tickets","url":"https:\/\/example.com\/disney\/tickets","folder":["Travel"]},""" +
                """{"title":"Last","url":"https:\/\/example.com\/last","folder":[]}""" +
                """]}""",
            value.toString(),
        )
    }

    @Test
    fun whenQueryGivenThenTitleUrlAndFolderNamesMatchIgnoringCase() = runTest {
        val disney = folder(folder(BOOKMARKS_ROOT, "Travel"), "Disney")
        bookmark(disney, "Park map", "https://example.com/map")
        bookmark(BOOKMARKS_ROOT, "DISNEY tickets", "https://example.com/t")
        bookmark(BOOKMARKS_ROOT, "Other", "https://disney.example.com/")
        bookmark(BOOKMARKS_ROOT, "Unrelated", "https://example.com/u?q=disney")

        val titles = titles(search("""{"query":"disney"}"""))

        assertEquals(listOf("Park map", "DISNEY tickets", "Other"), titles)
    }

    @Test
    fun whenFolderGivenThenOnlyThatFolderAndItsSubfoldersAreSearched() = runTest {
        val travel = folder(BOOKMARKS_ROOT, "Travel")
        bookmark(travel, "In travel", "https://example.com/1")
        bookmark(folder(travel, "Disney"), "In disney", "https://example.com/2")
        bookmark(BOOKMARKS_ROOT, "Outside", "https://example.com/3")

        val value = search("""{"folder":["Travel"]}""")

        assertEquals(listOf("In travel", "In disney"), titles(value))
        assertEquals("""["Travel","Disney"]""", value.getJSONArray("results").getJSONObject(1).getJSONArray("folder").toString())
    }

    @Test
    fun whenFolderDoesNotExistThenNoResults() = runTest {
        folder(BOOKMARKS_ROOT, "Travel")
        bookmark(BOOKMARKS_ROOT, "Paris", "https://example.com/")

        assertEquals("""{"results":[]}""", search("""{"folder":["Travel","Paris"]}""").toString())
    }

    @Test
    fun whenLimitOmittedThenTenAreReturnedAndLimitCapsTheRest() = runTest {
        repeat(60) { bookmark(BOOKMARKS_ROOT, "b$it", "https://example.com/$it") }

        assertEquals(10, titles(search("""{}""")).size)
        assertEquals((0 until 50).map { "b$it" }, titles(search("""{"limit":50}""")))
    }

    @Test
    fun whenBookmarkIsDeeperThanTenFoldersThenItIsLeftOut() = runTest {
        var parent = BOOKMARKS_ROOT
        repeat(11) { parent = folder(parent, "f$it") }
        bookmark(parent, "Too deep", "https://example.com/deep")

        assertEquals("""{"results":[]}""", search("""{}""").toString())
    }

    @Test
    fun whenTitleAndFolderNameTooLongThenCutToContractLimits() = runTest {
        bookmark(folder(BOOKMARKS_ROOT, "f".repeat(300)), "t".repeat(300), "https://example.com/" + "p".repeat(2000))

        val result = search("""{}""").getJSONArray("results").getJSONObject(0)

        assertEquals(256, result.getString("title").length)
        assertEquals(1280, result.getString("url").length)
        assertEquals(256, result.getJSONArray("folder").getString(0).length)
    }

    private suspend fun search(input: String): JSONObject = (testee.execute(JSONObject(input), context) as ClientToolResult.Success).value

    private fun titles(value: JSONObject): List<String> {
        val results = value.getJSONArray("results")
        return (0 until results.length()).map { results.getJSONObject(it).getString("title") }
    }

    private fun folder(
        parentId: String,
        name: String,
    ): String {
        val id = "folder${nextId++}"
        tree.getOrPut(parentId) { mutableListOf() } += FolderTreeItem(id, name, parentId, null)
        return id
    }

    private fun bookmark(
        parentId: String,
        title: String,
        url: String,
    ) {
        tree.getOrPut(parentId) { mutableListOf() } += FolderTreeItem("bookmark${nextId++}", title, parentId, url)
    }
}
