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
import com.duckduckgo.duckchat.impl.DuckChatConstants.HOST_DUCK_AI
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class GetSyncStatusHandlerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockSyncStatusHelper: SyncStatusHelper = mock()
    private val mockJsMessaging: JsMessaging = mock()

    private val callbackDataCaptor = argumentCaptor<JsCallbackData>()

    private lateinit var handler: GetSyncStatusHandler

    @Before
    fun setUp() {
        handler = GetSyncStatusHandler(
            syncStatusHelper = mockSyncStatusHelper,
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
    fun `when checking methods then returns getSyncStatus`() {
        val methods = handler.getJsMessageHandler().methods
        assertEquals(1, methods.size)
        assertEquals("getSyncStatus", methods[0])
    }

    @Test
    fun `when id is null then response is not sent`() {
        val jsMessage = createJsMessage(null)

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verifyNoInteractions(mockSyncStatusHelper)
        verifyNoInteractions(mockJsMessaging)
    }

    @Test
    fun `when id is empty then response is not sent`() {
        val jsMessage = createJsMessage("")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verifyNoInteractions(mockSyncStatusHelper)
        verifyNoInteractions(mockJsMessaging)
    }

    @Test
    fun `when sync status is retrieved successfully then success response is sent`() = runTest {
        val payload = JSONObject().apply {
            put("syncAvailable", true)
            put("userId", "user123")
            put("deviceId", "device456")
            put("deviceName", "MyDevice")
            put("deviceType", "mobile")
        }
        whenever(mockSyncStatusHelper.buildSyncStatusPayload()).thenReturn(payload)
        val jsMessage = createJsMessage("test-id")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        assertEquals("test-id", response.id)
        assertEquals("aiChat", response.featureName)
        assertEquals("getSyncStatus", response.method)

        val responseJson = response.params
        assertTrue(responseJson.getBoolean("ok"))
        val payloadResponse = responseJson.getJSONObject("payload")
        assertEquals(true, payloadResponse.getBoolean("syncAvailable"))
        assertEquals("user123", payloadResponse.getString("userId"))
        assertEquals("device456", payloadResponse.getString("deviceId"))
        assertEquals("MyDevice", payloadResponse.getString("deviceName"))
        assertEquals("mobile", payloadResponse.getString("deviceType"))
    }

    @Test
    fun `when sync status helper throws exception then error response is sent`() = runTest {
        whenever(mockSyncStatusHelper.buildSyncStatusPayload()).thenThrow(RuntimeException("test error"))
        val jsMessage = createJsMessage("test-id")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        assertEquals("test-id", response.id)
        assertEquals("aiChat", response.featureName)
        assertEquals("getSyncStatus", response.method)

        val responseJson = response.params
        assertFalse(responseJson.getBoolean("ok"))
        assertEquals("internal error", responseJson.getString("reason"))
    }

    private fun createJsMessage(id: String?): JsMessage {
        return JsMessage(
            context = "test",
            featureName = "aiChat",
            method = "getSyncStatus",
            id = id,
            params = JSONObject(),
        )
    }
}
