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
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.duckchat.impl.contextual.ContextualSheetCloseRequests
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class OpenTabClientToolTest {

    private val feature = FakeFeatureToggleFactory.create(AiChatBrowserToolsFeature::class.java)
    private val regularTabs: TabRepository = mock()
    private val tabRepositoryProvider = object : BrowserModeDataProvider<TabRepository> {
        override fun forMode(mode: BrowserMode): TabRepository = regularTabs
    }
    private val sheetCloseRequests = ContextualSheetCloseRequests()

    private val testee = OpenTabClientTool(feature, lazy { tabRepositoryProvider }, sheetCloseRequests)

    private val regular =
        ClientToolCallContext(chatTabId = CHAT_TAB, browserMode = BrowserMode.REGULAR, chatId = "chat-1")

    @Test
    fun whenParentAndToolFlagsEnabledThenToolIsEnabled() {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        assertFalse(testee.isEnabled())

        feature.openTab().setRawStoredState(Toggle.State(enable = true))
        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenToolIsAskedThenItAsksEveryTime() {
        assertTrue(testee.permissionMode is ClientToolPermissionMode.AskEveryTime)
    }

    @Test
    fun whenUrlIsAbsoluteHttpOrHttpsThenInputIsValid() {
        listOf("https://example.com/travel/paris", "http://example.com", "https://example.com/" + "a".repeat(1260)).forEach {
            assertTrue(it, testee.isValidInput(JSONObject().put("url", it)))
        }
    }

    @Test
    fun whenInputBreaksSchemaOrUrlIsNotAbsoluteWebUrlThenInvalid() {
        val inputs = listOf(
            JSONObject(),
            JSONObject().put("url", ""),
            JSONObject().put("url", 1),
            JSONObject().put("url", JSONObject.NULL),
            JSONObject("""{"url":["https://example.com"]}"""),
            JSONObject().put("url", "https://example.com").put("background", true),
            JSONObject().put("url", "https://example.com/" + "a".repeat(1261)),
            JSONObject().put("url", "javascript:alert(1)"),
            JSONObject().put("url", "file:///sdcard/doc.pdf"),
            JSONObject().put("url", "example.com/travel"),
            JSONObject().put("url", "/travel/paris"),
        )
        inputs.forEach { assertFalse(it.toString(), testee.isValidInput(it)) }
    }

    @Test
    fun whenUrlIsLongInAstralCodePointsThenLengthCountsCodePoints() {
        val url = "https://example.com/" + "🦆".repeat(1260)

        assertTrue(testee.isValidInput(JSONObject().put("url", url)))
    }

    @Test
    fun whenPromptDetailRequestedThenItIsTheFullUrl() = runTest {
        val url = "https://user@example.com/a?b=c#d"

        assertEquals(ClientToolPromptDetail.Shown(url), testee.promptDetail(JSONObject().put("url", url), regular))
    }

    @Test
    fun whenAllowedThenOpensTheAllowedUrlInANewSelectedTabAndClosesTheChatSheet() = runTest {
        whenever(regularTabs.add(url = "https://example.com/a?b=c", skipHome = true)).thenReturn(NEW_TAB)
        val closed = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sheetCloseRequests.tabIds.toList(closed) }
        val input = JSONObject().put("url", "https://example.com/a?b=c")

        val result = testee.execute(input, regular.copy(allowedDetail = "https://example.com/a?b=c"))

        assertEquals("""{"tabId":"$NEW_TAB"}""", (result as ClientToolResult.Success).value.toString())
        assertEquals(listOf(CHAT_TAB), closed)
    }

    @Test
    fun whenRunWithoutAnAllowedUrlThenNothingOpens() = runTest {
        val result = testee.execute(JSONObject().put("url", "https://example.com"), regular)

        assertEquals(ClientToolResult.Failure(ClientToolErrorCode.EXECUTION_FAILED), result)
        verify(regularTabs, never()).add(any(), any())
    }

    private companion object {
        const val CHAT_TAB = "11111111-1111-1111-1111-111111111111"
        const val NEW_TAB = "6f9619ff-8b86-d011-b42d-00c04fc964ff"
    }
}
