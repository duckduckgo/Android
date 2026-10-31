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

package com.duckduckgo.duckchat.impl.promo.messaging

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.impl.messaging.fakes.FakeJsMessaging
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PromoCampaignContentScopeJsMessageHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockPromoCampaignJSHelper: PromoCampaignJSHelper = mock()
    private val mockDuckAiHostProvider: DuckAiHostProvider = mock {
        on { getHost() } doReturn "duck.ai"
    }
    private val fakeJsMessaging = FakeJsMessaging()

    private val handler = PromoCampaignContentScopeJsMessageHandler(
        promoCampaignJSHelper = mockPromoCampaignJSHelper,
        duckAiHostProvider = mockDuckAiHostProvider,
        dispatchers = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
    ).getJsMessageHandler()

    @Test
    fun `when checking allowed domains, then returns duckduckgo dot com and duck dot ai`() {
        assertEquals(listOf("duckduckgo.com", "duck.ai"), handler.allowedDomains)
    }

    @Test
    fun `when checking feature name, then returns aiChat`() {
        assertEquals("aiChat", handler.featureName)
    }

    @Test
    fun `when checking methods, then returns the promo campaign methods`() {
        assertEquals(
            listOf(
                "getAIChatBonusRecord",
                "getAIChatNewBonusRecord",
                "aiChatBonusRedeemOutcome",
                "aiChatBonusDismiss",
                "aiChatBonusEnd",
            ),
            handler.methods,
        )
    }

    @Test
    fun `when message has a response, then it's sent back`() = runTest {
        val params = JSONObject().put("campaignName", "spring-2026")
        val response = JsCallbackData(JSONObject(), "aiChat", "getAIChatNewBonusRecord", "123")
        whenever(mockPromoCampaignJSHelper.processJsCallbackMessage("aiChat", "getAIChatNewBonusRecord", "123", params)).thenReturn(response)

        handler.process(jsMessage("getAIChatNewBonusRecord", id = "123", params = params), fakeJsMessaging, null)

        assertEquals(1, fakeJsMessaging.getResponseCount())
        assertEquals(response, fakeJsMessaging.getLastResponse())
    }

    @Test
    fun `when message has no response, then nothing is sent back`() = runTest {
        val params = JSONObject().put("reason", "optedOut")

        handler.process(jsMessage("aiChatBonusEnd", id = null, params = params), fakeJsMessaging, null)

        verify(mockPromoCampaignJSHelper).processJsCallbackMessage("aiChat", "aiChatBonusEnd", null, params)
        assertEquals(0, fakeJsMessaging.getResponseCount())
    }

    private fun jsMessage(
        method: String,
        id: String?,
        params: JSONObject,
    ): JsMessage = JsMessage(context = "test", featureName = "aiChat", method = method, params = params, id = id)
}
