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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ListOpenTabsClientToolTest {

    private val feature = FakeFeatureToggleFactory.create(AiChatBrowserToolsFeature::class.java)
    private val regularTabs: TabRepository = mock()
    private val fireTabs: TabRepository = mock()
    private val tabRepositoryProvider = object : BrowserModeDataProvider<TabRepository> {
        override fun forMode(mode: BrowserMode): TabRepository = when (mode) {
            BrowserMode.REGULAR -> regularTabs
            BrowserMode.FIRE -> fireTabs
        }
    }
    private val duckChat: DuckChat = mock {
        on { isDuckChatUrl(any()) } doReturn false
    }

    private val testee = ListOpenTabsClientTool(
        feature = feature,
        tabRepositoryProvider = lazy { tabRepositoryProvider },
        duckChat = duckChat,
        tabReadability = WebUrlTabReadability(),
    )

    private val regular = ClientToolCallContext(chatTabId = "chat", browserMode = BrowserMode.REGULAR)

    @Test
    fun whenOnlyParentFlagEnabledThenToolIsDisabled() {
        feature.self().setRawStoredState(Toggle.State(enable = true))

        assertFalse(testee.isEnabled())
    }

    @Test
    fun whenOnlyToolFlagEnabledThenToolIsDisabled() {
        feature.listOpenTabs().setRawStoredState(Toggle.State(enable = true))

        assertFalse(testee.isEnabled())
    }

    @Test
    fun whenParentAndToolFlagsEnabledThenToolIsEnabled() {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        feature.listOpenTabs().setRawStoredState(Toggle.State(enable = true))

        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenTabsListedThenOnlyWebPagesAreReturnedWithContractFields() = runTest {
        whenever(duckChat.isDuckChatUrl(Uri.parse("https://duck.ai/chat?q=1"))).thenReturn(true)
        givenRegularTabs(
            TabEntity(tabId = "a", url = "https://example.com/page?secret=1#top", title = "Example"),
            TabEntity(tabId = "b", url = "about:blank", title = "Blank"),
            TabEntity(tabId = "c", url = null, title = "New tab"),
            TabEntity(tabId = "d", url = "https://duck.ai/chat?q=1", title = "Duck.ai"),
            TabEntity(tabId = "e", url = "file:///sdcard/doc.pdf", title = "Doc"),
            TabEntity(tabId = "chat", url = "http://example.org/", title = null),
        )

        val value = success(testee.execute(JSONObject(), regular))

        assertEquals(
            """{"tabs":[""" +
                """{"tabId":"a","title":"Example","url":"https:\/\/example.com\/page","isCurrentTab":false,"isAttachable":true},""" +
                """{"tabId":"chat","title":"","url":"http:\/\/example.org\/","isCurrentTab":true,"isAttachable":true}""" +
                """]}""",
            value.toString(),
        )
    }

    @Test
    fun whenTabUrlHasUserInfoThenItIsRemoved() = runTest {
        givenRegularTabs(TabEntity(tabId = "a", url = "https://user:pass@example.com/path?x=1", title = "t"))

        val tab = success(testee.execute(JSONObject(), regular)).getJSONArray("tabs").getJSONObject(0)

        assertEquals("https://example.com/path", tab.getString("url"))
    }

    @Test
    fun whenRegularModeThenFireTabsAreNotRead() = runTest {
        givenRegularTabs()

        testee.execute(JSONObject(), regular)

        verify(fireTabs, never()).getTabs()
    }

    @Test
    fun whenMoreThanFiftyTabsThenFiftyAreReturned() = runTest {
        givenRegularTabs(*Array(60) { TabEntity(tabId = "t$it", url = "https://example.com/$it") })

        val tabs = success(testee.execute(JSONObject(), regular)).getJSONArray("tabs")

        assertEquals(50, tabs.length())
        assertEquals("t49", tabs.getJSONObject(49).getString("tabId"))
    }

    @Test
    fun whenLimitGivenThenAtMostThatManyTabsAreReturned() = runTest {
        givenRegularTabs(*Array(5) { TabEntity(tabId = "t$it", url = "https://example.com/$it") })

        val tabs = success(testee.execute(JSONObject("""{"limit":2}"""), regular)).getJSONArray("tabs")

        assertEquals(2, tabs.length())
    }

    @Test
    fun whenLimitArrivesAsWholeDoubleThenItIsAccepted() = runTest {
        givenRegularTabs(*Array(5) { TabEntity(tabId = "t$it", url = "https://example.com/$it") })
        val input = JSONObject().put("limit", 2.0)

        assertTrue(testee.isValidInput(input))
        assertEquals(2, success(testee.execute(input, regular)).getJSONArray("tabs").length())
    }

    @Test
    fun whenLimitOutOfRangeOrNotIntegerOrExtraKeyThenInvalidArguments() = runTest {
        givenRegularTabs()
        listOf("""{"limit":0}""", """{"limit":51}""", """{"limit":1.5}""", """{"limit":"5"}""", """{"limit":10,"window":"all"}""")
            .forEach { input ->
                assertFalse(input, testee.isValidInput(JSONObject(input)))
                assertEquals(
                    input,
                    ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS),
                    testee.execute(JSONObject(input), regular),
                )
            }
    }

    @Test
    fun whenTitleAndUrlTooLongThenCutToContractLimits() = runTest {
        givenRegularTabs(
            TabEntity(tabId = "a", url = "https://example.com/" + "p".repeat(2000), title = "t".repeat(300)),
        )

        val tab = success(testee.execute(JSONObject(), regular)).getJSONArray("tabs").getJSONObject(0)

        assertEquals(256, tab.getString("title").length)
        assertEquals(1280, tab.getString("url").length)
    }

    @Test
    fun whenCutWouldSplitSurrogatePairThenWholePairIsDropped() {
        val emoji = "🦆"
        val title = "t".repeat(255) + emoji

        val cut = ClientToolOutput.title(title)

        assertEquals("t".repeat(255), cut)
    }

    @Test
    fun whenSurrogatePairEndsExactlyAtLimitThenItIsKept() {
        val emoji = "🦆"
        val title = "t".repeat(254) + emoji + "more"

        assertEquals("t".repeat(254) + emoji, ClientToolOutput.title(title))
    }

    private fun success(result: ClientToolResult): JSONObject = (result as ClientToolResult.Success).value

    private suspend fun givenRegularTabs(vararg tabs: TabEntity) {
        whenever(regularTabs.getTabs()).thenReturn(tabs.toList())
    }
}
