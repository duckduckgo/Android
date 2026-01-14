/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.messaging.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.impl.DuckChatConstants.HOST_DUCK_AI
import com.duckduckgo.duckchat.impl.messaging.fakes.FakeJsMessaging
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.js.messaging.api.JsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@RunWith(AndroidJUnit4::class)
class SetAIChatHistoryEnabledHandlerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockDuckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private val dispatchers: DispatcherProvider = coroutineTestRule.testDispatcherProvider
    private val appCoroutineScope: CoroutineScope = coroutineTestRule.testScope
    private val fakeJsMessaging = FakeJsMessaging()

    private lateinit var handler: SetAIChatHistoryEnabledHandler

    @Before
    fun setUp() {
        handler = SetAIChatHistoryEnabledHandler(
            duckChatFeatureRepository = mockDuckChatFeatureRepository,
            dispatchers = dispatchers,
            appCoroutineScope = appCoroutineScope,
        )
    }

    @Test
    fun `when checking allowed domains then returns duckduckgo dot com and duck dot ai`() {
        val domains = handler.getJsMessageHandler().allowedDomains
        assertEquals(2, domains.size)
        assertEquals("duckduckgo.com", domains[0])
        assertEquals(HOST_DUCK_AI, domains[1])
    }

    @Test
    fun `when checking feature name then returns aiChat`() {
        assertEquals("aiChat", handler.getJsMessageHandler().featureName)
    }

    @Test
    fun `when checking methods then returns setAIChatHistoryEnabled`() {
        val methods = handler.getJsMessageHandler().methods
        assertEquals(1, methods.size)
        assertEquals("setAIChatHistoryEnabled", methods[0])
    }

    @Test
    fun `when enabled parameter is missing then repository is not called`() = runTest {
        val jsMessage = createJsMessage(JSONObject())
        handler.getJsMessageHandler().process(jsMessage, fakeJsMessaging, null)
        verifyNoInteractions(mockDuckChatFeatureRepository)
    }

    @Test
    fun `when enabled parameter is true then repository is called with true`() = runTest {
        val jsMessage = createJsMessage(JSONObject().apply { put("enabled", true) })
        handler.getJsMessageHandler().process(jsMessage, fakeJsMessaging, null)
        verify(mockDuckChatFeatureRepository).setAIChatHistoryEnabled(true)
    }

    @Test
    fun `when enabled parameter is false then repository is called with false`() = runTest {
        val jsMessage = createJsMessage(JSONObject().apply { put("enabled", false) })
        handler.getJsMessageHandler().process(jsMessage, fakeJsMessaging, null)
        verify(mockDuckChatFeatureRepository).setAIChatHistoryEnabled(false)
    }

    private fun createJsMessage(params: JSONObject): JsMessage {
        return JsMessage(
            context = "test",
            featureName = "aiChat",
            method = "setAIChatHistoryEnabled",
            id = "test-id",
            params = params,
        )
    }
}
