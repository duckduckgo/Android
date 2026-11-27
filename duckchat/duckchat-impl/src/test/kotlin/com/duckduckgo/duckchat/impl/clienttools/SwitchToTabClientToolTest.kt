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
import com.duckduckgo.duckchat.impl.contextual.ContextualSheetCloseRequests
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class SwitchToTabClientToolTest {

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
    private val sheetCloseRequests = ContextualSheetCloseRequests()

    private val testee = SwitchToTabClientTool(feature, lazy { tabRepositoryProvider }, duckChat, sheetCloseRequests)

    private val regular =
        ClientToolCallContext(chatTabId = CHAT_TAB, browserMode = BrowserMode.REGULAR, chatId = "chat-1")

    @Test
    fun whenParentAndToolFlagsEnabledThenToolIsEnabled() {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        assertFalse(testee.isEnabled())

        feature.switchToTab().setRawStoredState(Toggle.State(enable = true))
        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenToolRunsWithoutPromptThenItsModeIsAuto() {
        assertEquals(ClientToolPermissionMode.Auto, testee.permissionMode)
    }

    @Test
    fun whenInputBreaksSchemaThenInvalidArguments() = runTest {
        listOf("""{}""", """{"tabId":1}""", """{"tabId":null}""", """{"tabId":"$TAB","focus":true}""").forEach { input ->
            assertFalse(input, testee.isValidInput(JSONObject(input)))
            assertEquals(input, ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS), testee.execute(JSONObject(input), regular))
        }
        assertTrue(testee.isValidInput(JSONObject("""{"tabId":"$TAB"}""")))
    }

    @Test
    fun whenTabIsAWebPageThenItIsSelectedAndTheChatSheetCloses() = runTest {
        givenTab(TabEntity(tabId = TAB, url = "https://example.com/"))
        val closed = collectCloseRequests()

        val result = testee.execute(JSONObject().put("tabId", TAB), regular)

        assertEquals("""{"tabId":"$TAB"}""", (result as ClientToolResult.Success).value.toString())
        verify(regularTabs).select(TAB)
        assertEquals(listOf(CHAT_TAB), closed)
    }

    @Test
    fun whenTabIsTheChatsOwnTabThenItIsSelectedAndTheSheetStays() = runTest {
        givenTab(TabEntity(tabId = CHAT_TAB, url = "https://example.com/"))
        val closed = collectCloseRequests()

        testee.execute(JSONObject().put("tabId", CHAT_TAB), regular)

        verify(regularTabs).select(CHAT_TAB)
        assertTrue(closed.isEmpty())
    }

    @Test
    fun whenTabIsUnknownThenNotFoundAndNothingChanges() = runTest {
        val closed = collectCloseRequests()

        assertEquals(ClientToolResult.Failure(ClientToolErrorCode.NOT_FOUND), testee.execute(JSONObject().put("tabId", TAB), regular))
        verify(regularTabs, never()).select(any())
        assertTrue(closed.isEmpty())
    }

    @Test
    fun whenTabIsDuckAiOrNotAWebPageThenNotFound() = runTest {
        whenever(duckChat.isDuckChatUrl(Uri.parse("https://duck.ai/chat"))).thenReturn(true)
        listOf("https://duck.ai/chat", "about:blank", null).forEach { url ->
            givenTab(TabEntity(tabId = TAB, url = url))

            assertEquals(ClientToolResult.Failure(ClientToolErrorCode.NOT_FOUND), testee.execute(JSONObject().put("tabId", TAB), regular))
        }
        verify(regularTabs, never()).select(any())
    }

    @Test
    fun whenRegularModeThenFireTabsAreNotSearched() = runTest {
        whenever(fireTabs.getTab(TAB)).thenReturn(TabEntity(tabId = TAB, url = "https://example.com/"))

        assertEquals(ClientToolResult.Failure(ClientToolErrorCode.NOT_FOUND), testee.execute(JSONObject().put("tabId", TAB), regular))
    }

    private suspend fun givenTab(tab: TabEntity) {
        whenever(regularTabs.getTab(tab.tabId)).thenReturn(tab)
    }

    private fun TestScope.collectCloseRequests(): List<String> {
        val closed = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sheetCloseRequests.tabIds.toList(closed) }
        return closed
    }

    private companion object {
        const val CHAT_TAB = "11111111-1111-1111-1111-111111111111"
        const val TAB = "6f9619ff-8b86-d011-b42d-00c04fc964ff"
    }
}
