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
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class FindInPageClientToolTest {

    private val feature = FakeFeatureToggleFactory.create(AiChatBrowserToolsFeature::class.java)
    private val tabs: TabRepository = mock()
    private val tabRepositoryProvider = object : BrowserModeDataProvider<TabRepository> {
        override fun forMode(mode: BrowserMode): TabRepository = tabs
    }
    private val pageContexts = FakeTabPageContextRepository()
    private val duckChat: DuckChat = mock()

    private val testee = FindInPageClientTool(feature, CachedTabPages(lazy { tabRepositoryProvider }, pageContexts, duckChat))

    private val context = ClientToolCallContext(chatTabId = HOST_TAB, browserMode = BrowserMode.REGULAR, chatId = "chat-1", contextual = true)

    @Test
    fun whenParentAndToolFlagsEnabledThenToolIsEnabled() {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        assertFalse(testee.isEnabled())

        feature.findInPage().setRawStoredState(Toggle.State(enable = true))
        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenInputMatchesSchemaThenValid() {
        listOf(
            """{"query":"paris"}""",
            """{"query":"paris","tabId":"other","caseSensitive":true,"maxMatches":50}""",
            JSONObject().put("query", "🦆".repeat(1000)).toString(),
        ).forEach { assertTrue(it, testee.isValidInput(JSONObject(it))) }
    }

    @Test
    fun whenInputBreaksSchemaThenInvalidArguments() = runTest {
        listOf(
            """{}""",
            """{"query":""}""",
            JSONObject().put("query", "a".repeat(1001)).toString(),
            """{"query":"paris","maxMatches":0}""",
            """{"query":"paris","maxMatches":51}""",
            """{"query":"paris","caseSensitive":"yes"}""",
            """{"query":"paris","tabId":1}""",
            """{"query":"paris","limit":5}""",
        ).forEach { input ->
            assertFalse(input, testee.isValidInput(JSONObject(input)))
            assertEquals(input, ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS), testee.execute(JSONObject(input), context))
        }
    }

    @Test
    fun whenTabIdOmittedThenTheChatsTabIsSearchedIgnoringCase() = runTest {
        givenPage(HOST_TAB, "https://example.com/travel?ref=1#top", "Paris in spring. We love paris.")

        val value = success(testee.execute(JSONObject("""{"query":"PARIS"}"""), context))

        assertEquals(
            """{"tabId":"host","title":"Travel","url":"https:\/\/example.com\/travel","query":"PARIS","matchCount":2,""" +
                """"matches":[{"snippet":"Paris in spring. We love paris.","matchIndex":0},""" +
                """{"snippet":"Paris in spring. We love paris.","matchIndex":1}],""" +
                """"truncated":false,"contentTruncated":false,"matchIndexesReliable":false}""",
            value.toString(),
        )
    }

    @Test
    fun whenCaseSensitiveThenOnlyExactCaseMatches() = runTest {
        givenPage(OTHER_TAB, "https://example.com/", "Paris and paris")

        val value = success(testee.execute(JSONObject("""{"query":"paris","tabId":"other","caseSensitive":true}"""), context))

        assertEquals(1, value.getInt("matchCount"))
    }

    @Test
    fun whenMoreMatchesThanMaxThenMatchesStopThereAndAreMarkedTruncated() = runTest {
        givenPage(OTHER_TAB, "https://example.com/", "duck ".repeat(20))

        val value = success(testee.execute(JSONObject("""{"query":"duck","tabId":"other","maxMatches":3}"""), context))

        assertEquals(20, value.getInt("matchCount"))
        assertEquals(3, value.getJSONArray("matches").length())
        assertEquals(2, value.getJSONArray("matches").getJSONObject(2).getInt("matchIndex"))
        assertTrue(value.getBoolean("truncated"))
    }

    @Test
    fun whenMatchIsInLongTextThenSnippetIsCutAroundItWithWhitespaceCollapsed() = runTest {
        givenPage(OTHER_TAB, "https://example.com/", "a".repeat(500) + " \n\n needle  here " + "b".repeat(500))

        val snippet = success(testee.execute(JSONObject("""{"query":"needle","tabId":"other"}"""), context))
            .getJSONArray("matches").getJSONObject(0).getString("snippet")

        assertTrue(snippet, snippet.length <= 100)
        assertTrue(snippet, snippet.contains("needle here"))
    }

    @Test
    fun whenNothingMatchesThenNoMatches() = runTest {
        givenPage(OTHER_TAB, "https://example.com/", "nothing to see")

        val value = success(testee.execute(JSONObject("""{"query":"paris","tabId":"other"}"""), context))

        assertEquals(0, value.getInt("matchCount"))
        assertEquals(0, value.getJSONArray("matches").length())
        assertFalse(value.getBoolean("truncated"))
    }

    @Test
    fun whenExtractionWasTruncatedThenContentTruncatedIsTrue() = runTest {
        givenPage(OTHER_TAB, "https://example.com/", "paris", truncated = true)

        assertTrue(success(testee.execute(JSONObject("""{"query":"paris","tabId":"other"}"""), context)).getBoolean("contentTruncated"))
    }

    @Test
    fun whenTabIsUnknownThenNotFound() = runTest {
        assertEquals(
            ClientToolResult.Failure(ClientToolErrorCode.NOT_FOUND),
            testee.execute(JSONObject("""{"query":"paris","tabId":"missing"}"""), context),
        )
    }

    @Test
    fun whenNoContextIsCachedThenUnavailable() = runTest {
        whenever(tabs.getTab(OTHER_TAB)).thenReturn(TabEntity(tabId = OTHER_TAB, url = "https://example.com/"))

        assertEquals(
            ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE),
            testee.execute(JSONObject("""{"query":"paris","tabId":"other"}"""), context),
        )
    }

    private suspend fun givenPage(
        tabId: String,
        url: String,
        content: String,
        truncated: Boolean = false,
    ) {
        whenever(tabs.getTab(tabId)).thenReturn(TabEntity(tabId = tabId, url = url, title = "Tab title"))
        val pageContext = JSONObject()
            .put("title", "Travel")
            .put("content", content)
            .put("truncated", truncated)
            .put("fullContentLength", content.length)
            .put("attachable", true)
        pageContexts.storePageContext(tabId, url, pageContext.toString())
    }

    private fun success(result: ClientToolResult): JSONObject = (result as ClientToolResult.Success).value

    private companion object {
        const val HOST_TAB = "host"
        const val OTHER_TAB = "other"
    }
}
