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

package com.duckduckgo.sync.impl.messaging

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.pixels.SyncAccountOperation
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.store.SyncStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class GetScopedSyncAuthTokenHandlerTest {

    private val mockSyncApi: SyncApi = mock()
    private val mockSyncStore: SyncStore = mock()
    private val mockDeviceSyncState: DeviceSyncState = mock()
    private val mockSyncPixels: SyncPixels = mock()
    private val mockJsMessaging: JsMessaging = mock()

    val callbackDataCaptor = argumentCaptor<JsCallbackData>()

    private lateinit var handler: GetScopedSyncAuthTokenHandler

    @Before
    fun setUp() {
        handler = GetScopedSyncAuthTokenHandler(
            syncApi = mockSyncApi,
            syncStore = mockSyncStore,
            deviceSyncState = mockDeviceSyncState,
            syncPixels = mockSyncPixels,
        )
    }

    @Test
    fun `when checking allowed domains then returns duckduckgo dot com and duck dot ai`() {
        val domains = handler.getJsMessageHandler().allowedDomains
        assertEquals(2, domains.size)
        assertEquals("duckduckgo.com", domains[0])
        assertEquals("duck.ai", domains[1])
    }

    @Test
    fun `when checking feature name then returns aiChat`() {
        assertEquals(FEATURE_NAME, handler.getJsMessageHandler().featureName)
    }

    @Test
    fun `when checking methods then returns getScopedSyncAuthToken`() {
        val methods = handler.getJsMessageHandler().methods
        assertEquals(1, methods.size)
        assertEquals(METHOD_NAME, methods[0])
    }

    @Test
    fun `when id is null then no response is sent`() {
        val jsMessage = createJsMessage(null)
        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)
        verifyNoResponse()
    }

    @Test
    fun `when id is empty then no response is sent`() {
        val jsMessage = createJsMessage("")
        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)
        verifyNoResponse()
    }

    @Test
    fun `when sync is disabled then error response is sent`() {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(false)
        val jsMessage = createJsMessage(TEST_MESSAGE_ID)

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        assertEquals(TEST_MESSAGE_ID, response.id)
        assertEquals(FEATURE_NAME, response.featureName)
        assertEquals(METHOD_NAME, response.method)
        verifyErrorResponse(response.params, "sync unavailable")
    }

    @Test
    fun `when sync is off then error response is sent`() {
        configureSyncEnabled()
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(DeviceSyncState.SyncAccountState.SignedOut)
        val jsMessage = createJsMessage(TEST_MESSAGE_ID)

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        verifyErrorResponse(response.params, "sync off")
    }

    @Test
    fun `when token is null then error response is sent`() {
        configureSyncEnabled()
        configureSignedIn()
        whenever(mockSyncStore.token).thenReturn(null)
        val jsMessage = createJsMessage(TEST_MESSAGE_ID)

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        assertEquals(TEST_MESSAGE_ID, response.id)
        assertEquals(FEATURE_NAME, response.featureName)
        assertEquals(METHOD_NAME, response.method)
        verifyErrorResponse(response.params, "token unavailable")
    }

    @Test
    fun `when token is empty then error response is sent`() {
        configureSyncEnabled()
        configureSignedIn()
        whenever(mockSyncStore.token).thenReturn("")
        val jsMessage = createJsMessage(TEST_MESSAGE_ID)

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        verifyErrorResponse(response.params, "token unavailable")
    }

    @Test
    fun `when rescope token succeeds then success response with token is sent`() {
        configureSyncEnabled()
        configureSignedIn()
        whenever(mockSyncStore.token).thenReturn(ORIGINAL_TOKEN)
        whenever(mockSyncApi.rescopeToken(ORIGINAL_TOKEN, SCOPE)).thenReturn(Result.Success(SCOPED_TOKEN))
        val jsMessage = createJsMessage(TEST_MESSAGE_ID)

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        assertEquals(TEST_MESSAGE_ID, response.id)
        assertEquals(FEATURE_NAME, response.featureName)
        assertEquals(METHOD_NAME, response.method)
        verifySuccessResponse(response.params)
    }

    @Test
    fun `when rescope token fails then error response with reason is sent`() {
        configureSyncEnabled()
        configureSignedIn()
        whenever(mockSyncStore.token).thenReturn(ORIGINAL_TOKEN)
        whenever(mockSyncApi.rescopeToken(ORIGINAL_TOKEN, SCOPE)).thenReturn(Result.Error(code = ERROR_CODE, reason = ERROR_REASON))
        val jsMessage = createJsMessage(TEST_MESSAGE_ID)

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        verifyErrorResponse(response.params, ERROR_REASON)
    }

    @Test
    fun `when rescope token fails then error pixel is fired`() {
        configureSyncEnabled()
        configureSignedIn()
        whenever(mockSyncStore.token).thenReturn(ORIGINAL_TOKEN)
        val error = Result.Error(code = ERROR_CODE, reason = ERROR_REASON)
        whenever(mockSyncApi.rescopeToken(ORIGINAL_TOKEN, SCOPE)).thenReturn(error)
        val jsMessage = createJsMessage(TEST_MESSAGE_ID)

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockSyncPixels).fireSyncAccountErrorPixel(error, SyncAccountOperation.RESCOPE_TOKEN)
    }

    @Test
    fun `when rescope token throws exception then internal error response is sent`() {
        configureSyncEnabled()
        configureSignedIn()
        whenever(mockSyncStore.token).thenReturn(ORIGINAL_TOKEN)
        whenever(mockSyncApi.rescopeToken(ORIGINAL_TOKEN, SCOPE)).thenThrow(RuntimeException("Network error"))
        val jsMessage = createJsMessage(TEST_MESSAGE_ID)

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        verifyErrorResponse(response.params, "internal error")
    }

    private fun configureSyncEnabled() {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(true)
    }

    private fun configureSignedIn() {
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(DeviceSyncState.SyncAccountState.SignedIn("userId", emptyList()))
    }

    private fun verifyNoResponse() {
        verifyNoInteractions(mockJsMessaging)
        verifyNoInteractions(mockSyncApi)
        verifyNoInteractions(mockSyncStore)
        verifyNoInteractions(mockDeviceSyncState)
    }

    private fun verifySuccessResponse(params: JSONObject) {
        assertTrue(params.getBoolean("ok"))
        val payload = params.getJSONObject("payload")
        assertEquals(SCOPED_TOKEN, payload.getString("token"))
    }

    private fun verifyErrorResponse(params: JSONObject, expectedReason: String) {
        assertFalse(params.getBoolean("ok"))
        assertEquals(expectedReason, params.getString("reason"))
    }

    private fun createJsMessage(id: String?): JsMessage {
        return JsMessage(
            context = "test",
            featureName = FEATURE_NAME,
            method = METHOD_NAME,
            id = id,
            params = JSONObject(),
        )
    }

    companion object {
        private const val TEST_MESSAGE_ID = "test-id"
        private const val FEATURE_NAME = "aiChat"
        private const val METHOD_NAME = "getScopedSyncAuthToken"
        private const val ORIGINAL_TOKEN = "original-token-123"
        private const val SCOPED_TOKEN = "scoped-token-456"
        private const val SCOPE = "ai_chats"
        private const val ERROR_CODE = 401
        private const val ERROR_REASON = "Unauthorized"
    }
}
