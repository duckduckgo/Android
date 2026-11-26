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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ReadTabContentClientToolTest {

    private val feature = FakeFeatureToggleFactory.create(AiChatBrowserToolsFeature::class.java)
    private val tabs: TabRepository = mock()
    private val tabRepositoryProvider = object : BrowserModeDataProvider<TabRepository> {
        override fun forMode(mode: BrowserMode): TabRepository = tabs
    }
    private val pageContexts = FakeTabPageContextRepository()
    private val duckChat: DuckChat = mock()

    private val testee = ReadTabContentClientTool(feature, CachedTabPages(lazy { tabRepositoryProvider }, pageContexts, duckChat))

    private val context = ClientToolCallContext(chatTabId = HOST_TAB, browserMode = BrowserMode.REGULAR, chatId = "chat-1", contextual = true)

    @Test
    fun whenParentAndToolFlagsEnabledThenToolIsEnabled() {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        assertFalse(testee.isEnabled())

        feature.readTabContent().setRawStoredState(Toggle.State(enable = true))
        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenInputBreaksSchemaThenInvalidArguments() = runTest {
        listOf("""{"tabId":1}""", """{"tabId":"a","extra":true}""").forEach { input ->
            assertFalse(input, testee.isValidInput(JSONObject(input)))
            assertEquals(input, ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS), testee.execute(JSONObject(input), context))
        }
        assertTrue(testee.isValidInput(JSONObject("{}")))
    }

    @Test
    fun whenTabIdOmittedThenTheChatsTabIsReadWithoutQueryOrFragment() = runTest {
        givenTab(HOST_TAB, "https://example.com/paris?ref=1#top")
        givenPageContext(HOST_TAB, "https://example.com/paris?ref=2", pageContext(title = "Paris", content = "Eiffel tower", fullContentLength = 12))

        val value = success(testee.execute(JSONObject(), context))

        assertEquals(
            """{"tabId":"host","title":"Paris","url":"https:\/\/example.com\/paris","content":"Eiffel tower",""" +
                """"truncated":false,"fullContentLength":12,"attachable":true,"attached":false}""",
            value.toString(),
        )
    }

    @Test
    fun whenContentIsLongerThanTheLimitThenItIsCutAndMarkedTruncated() = runTest {
        givenTab(OTHER_TAB, "https://example.com/")
        givenPageContext(OTHER_TAB, "https://example.com/", pageContext(content = "a".repeat(3500), fullContentLength = 3500))

        val value = success(testee.execute(JSONObject().put("tabId", OTHER_TAB), context))

        assertEquals(3000, value.getString("content").length)
        assertTrue(value.getBoolean("truncated"))
        assertEquals(3500, value.getInt("fullContentLength"))
    }

    @Test
    fun whenExtractionAlreadyTruncatedThenTruncatedIsKept() = runTest {
        givenTab(OTHER_TAB, "https://example.com/")
        givenPageContext(OTHER_TAB, "https://example.com/", pageContext(content = "short", truncated = true, fullContentLength = 90000))

        val value = success(testee.execute(JSONObject().put("tabId", OTHER_TAB), context))

        assertTrue(value.getBoolean("truncated"))
        assertEquals(90000, value.getInt("fullContentLength"))
    }

    @Test
    fun whenTabIsUnknownThenNotFound() = runTest {
        assertEquals(ClientToolResult.Failure(ClientToolErrorCode.NOT_FOUND), testee.execute(JSONObject().put("tabId", "missing"), context))
    }

    @Test
    fun whenTabIsNotAWebPageThenUnavailable() = runTest {
        givenTab(OTHER_TAB, "duck://newtab")

        assertEquals(ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE), testee.execute(JSONObject().put("tabId", OTHER_TAB), context))
    }

    @Test
    fun whenTabIsADuckAiChatThenUnavailable() = runTest {
        givenTab(OTHER_TAB, "https://duck.ai/")
        whenever(duckChat.isDuckChatUrl(any())).thenReturn(true)

        assertEquals(ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE), testee.execute(JSONObject().put("tabId", OTHER_TAB), context))
    }

    @Test
    fun whenNoContextIsCachedThenUnavailable() = runTest {
        givenTab(OTHER_TAB, "https://example.com/")

        assertEquals(ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE), testee.execute(JSONObject().put("tabId", OTHER_TAB), context))
    }

    @Test
    fun whenCachedContextIsForAnotherAddressThenUnavailable() = runTest {
        givenTab(OTHER_TAB, "https://example.com/new")
        givenPageContext(OTHER_TAB, "https://example.com/old", pageContext())

        assertEquals(ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE), testee.execute(JSONObject().put("tabId", OTHER_TAB), context))
    }

    @Test
    fun whenPageIsNotAttachableThenUnavailable() = runTest {
        givenTab(OTHER_TAB, "https://example.com/")
        givenPageContext(OTHER_TAB, "https://example.com/", pageContext(attachable = false))

        assertEquals(ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE), testee.execute(JSONObject().put("tabId", OTHER_TAB), context))
    }

    @Test
    fun whenTitleAndUrlTooLongThenCutToContractLimits() = runTest {
        val longUrl = "https://example.com/" + "p".repeat(2000)
        givenTab(OTHER_TAB, longUrl)
        givenPageContext(OTHER_TAB, longUrl, pageContext(title = "t".repeat(300)))

        val value = success(testee.execute(JSONObject().put("tabId", OTHER_TAB), context))

        assertEquals("t".repeat(256), value.getString("title"))
        assertEquals(longUrl.take(1280), value.getString("url"))
    }

    @Test
    fun whenContentCutWouldSplitSurrogatePairThenWholePairIsDropped() = runTest {
        givenTab(OTHER_TAB, "https://example.com/")
        givenPageContext(OTHER_TAB, "https://example.com/", pageContext(content = "a".repeat(2999) + "🦆🦆"))

        val value = success(testee.execute(JSONObject().put("tabId", OTHER_TAB), context))

        assertEquals("a".repeat(2999), value.getString("content"))
        assertTrue(value.getBoolean("truncated"))
    }

    private suspend fun givenTab(
        tabId: String,
        url: String,
    ) {
        whenever(tabs.getTab(tabId)).thenReturn(TabEntity(tabId = tabId, url = url, title = "Tab title", position = 0))
    }

    private suspend fun givenPageContext(
        tabId: String,
        url: String,
        json: JSONObject,
    ) {
        pageContexts.storePageContext(tabId, url, json.toString())
    }

    private fun pageContext(
        title: String = "Title",
        content: String = "content",
        truncated: Boolean = false,
        fullContentLength: Int = content.length,
        attachable: Boolean = true,
    ) = JSONObject()
        .put("title", title)
        .put("content", content)
        .put("truncated", truncated)
        .put("fullContentLength", fullContentLength)
        .put("attachable", attachable)

    private fun success(result: ClientToolResult): JSONObject = (result as ClientToolResult.Success).value

    private companion object {
        const val HOST_TAB = "host"
        const val OTHER_TAB = "other"
    }
}
