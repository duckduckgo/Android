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
import org.json.JSONArray
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
class CloseTabsClientToolTest {

    private val feature = FakeFeatureToggleFactory.create(AiChatBrowserToolsFeature::class.java)
    private val regularTabs: TabRepository = mock()
    private val tabRepositoryProvider = object : BrowserModeDataProvider<TabRepository> {
        override fun forMode(mode: BrowserMode): TabRepository = regularTabs
    }
    private val duckChat: DuckChat = mock {
        on { isDuckChatUrl(any()) } doReturn false
    }

    private val testee = CloseTabsClientTool(feature, lazy { tabRepositoryProvider }, duckChat)

    private val regular = ClientToolCallContext(chatTabId = HOST, browserMode = BrowserMode.REGULAR, chatId = "chat-1")

    @Test
    fun whenParentAndToolFlagsEnabledThenToolIsEnabled() {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        assertFalse(testee.isEnabled())

        feature.closeTabs().setRawStoredState(Toggle.State(enable = true))
        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenToolIsAskedThenItUsesTheNormalPrompt() {
        assertTrue(testee.permissionMode is ClientToolPermissionMode.Ask)
    }

    @Test
    fun whenInputMatchesSchemaThenValid() {
        listOf(JSONObject(), JSONObject().put("tabIds", JSONArray(List(50) { A }))).forEach {
            assertTrue(it.toString(), testee.isValidInput(it))
        }
    }

    @Test
    fun whenInputBreaksSchemaThenInvalidArguments() = runTest {
        listOf(
            """{"tabIds":[]}""",
            JSONObject().put("tabIds", JSONArray(List(51) { A })).toString(),
            """{"tabIds":"$A"}""",
            """{"tabIds":[1]}""",
            """{"tabId":"$A"}""",
            """{"tabIds":["$A"],"windowId":1}""",
        ).forEach { input ->
            assertFalse(input, testee.isValidInput(JSONObject(input)))
            assertEquals(input, ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS), testee.execute(JSONObject(input), regular))
        }
    }

    @Test
    fun whenTabIdsOmittedThenTheHostTabCloses() = runTest {
        givenTab(HOST, "https://example.com/")

        val value = success(testee.execute(JSONObject(), regular))

        assertEquals("""{"tabs":[{"tabId":"$HOST","closed":true}]}""", value.toString())
        verify(regularTabs).deleteTabs(listOf(HOST))
    }

    @Test
    fun whenTabIdsOmittedAndTheChatIsADuckAiTabThenInvalidArguments() = runTest {
        whenever(duckChat.isDuckChatUrl(Uri.parse("https://duck.ai/chat"))).thenReturn(true)
        givenTab(HOST, "https://duck.ai/chat")

        assertEquals(ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS), testee.execute(JSONObject(), regular))
        verify(regularTabs, never()).deleteTabs(any())
    }

    @Test
    fun whenSomeTabsCannotBeClosedThenOnlyTheRestCloseAndEachIsReported() = runTest {
        whenever(duckChat.isDuckChatUrl(Uri.parse("https://duck.ai/chat"))).thenReturn(true)
        givenTab(A, "https://example.com/a")
        givenTab(B, "https://duck.ai/chat")
        givenTab(C, "about:blank")
        val input = JSONObject().put("tabIds", JSONArray(listOf(A, B, C, UNKNOWN, A, "tab-1")))

        val value = success(testee.execute(input, regular))

        assertEquals(
            """{"tabs":[{"tabId":"$A","closed":true},{"tabId":"$B","closed":false},""" +
                """{"tabId":"$C","closed":false},{"tabId":"$UNKNOWN","closed":false}]}""",
            value.toString(),
        )
        verify(regularTabs).deleteTabs(listOf(A))
    }

    @Test
    fun whenNoTabCanBeClosedThenNothingIsDeleted() = runTest {
        val value = success(testee.execute(JSONObject().put("tabIds", JSONArray(listOf(UNKNOWN))), regular))

        assertEquals("""{"tabs":[{"tabId":"$UNKNOWN","closed":false}]}""", value.toString())
        verify(regularTabs, never()).deleteTabs(any())
    }

    private fun success(result: ClientToolResult): JSONObject = (result as ClientToolResult.Success).value

    private suspend fun givenTab(
        tabId: String,
        url: String,
    ) {
        whenever(regularTabs.getTab(tabId)).thenReturn(TabEntity(tabId = tabId, url = url))
    }

    private companion object {
        const val HOST = "11111111-1111-1111-1111-111111111111"
        const val A = "6f9619ff-8b86-d011-b42d-00c04fc964fa"
        const val B = "6F9619FF-8B86-D011-B42D-00C04FC964FB"
        const val C = "6f9619ff-8b86-d011-b42d-00c04fc964fc"
        const val UNKNOWN = "6f9619ff-8b86-d011-b42d-00c04fc964fd"
    }
}
