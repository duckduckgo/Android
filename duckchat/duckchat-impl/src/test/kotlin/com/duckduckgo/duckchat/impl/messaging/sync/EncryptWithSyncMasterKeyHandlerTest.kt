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

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.impl.DuckChatConstants.HOST_DUCK_AI
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState.SignedIn
import com.duckduckgo.sync.api.SyncCrypto
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class EncryptWithSyncMasterKeyHandlerTest {

    private val mockCrypto: SyncCrypto = mock()
    private val mockDeviceSyncState: DeviceSyncState = mock()
    private val mockJsMessaging: JsMessaging = mock()

    val callbackDataCaptor = argumentCaptor<JsCallbackData>()

    private lateinit var handler: EncryptWithSyncMasterKeyHandler

    @Before
    fun setUp() {
        handler = EncryptWithSyncMasterKeyHandler(
            crypto = mockCrypto,
            deviceSyncState = mockDeviceSyncState,
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
        assertEquals(FEATURE_NAME, handler.getJsMessageHandler().featureName)
    }

    @Test
    fun `when checking methods then returns encryptWithSyncMasterKey`() {
        val methods = handler.getJsMessageHandler().methods
        assertEquals(1, methods.size)
        assertEquals(METHOD_NAME, methods[0])
    }

    @Test
    fun `when id is null then no response is sent`() {
        val jsMessage = createJsMessage(null, JSONObject())
        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)
        verifyNoResponse()
    }

    @Test
    fun `when id is empty then no response is sent`() {
        val jsMessage = createJsMessage("", JSONObject())
        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)
        verifyNoResponse()
    }

    @Test
    fun `when sync is disabled then error response is sent`() {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(false)
        val jsMessage = createJsMessage(TEST_MESSAGE_ID, JSONObject().apply { put("data", BASE64_URL_DATA) })

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
        val jsMessage = createJsMessage(TEST_MESSAGE_ID, JSONObject().apply { put("data", BASE64_URL_DATA) })

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        verifyErrorResponse(response.params, "sync off")
    }

    @Test
    fun `when data is missing then error response is sent`() {
        configureSyncEnabled()
        configureSignedIn()
        val jsMessage = createJsMessage(TEST_MESSAGE_ID, JSONObject())

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        verifyErrorResponse(response.params, "invalid parameters")
    }

    @Test
    fun `when data is empty then error response is sent`() {
        configureSyncEnabled()
        configureSignedIn()
        val jsMessage = createJsMessage(TEST_MESSAGE_ID, JSONObject().apply { put("data", "") })

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        verifyErrorResponse(response.params, "invalid parameters")
    }

    @Test
    fun `when base64Url decode fails then error response is sent`() {
        configureSyncEnabled()
        configureSignedIn()
        val jsMessage = createJsMessage(TEST_MESSAGE_ID, JSONObject().apply { put("data", "invalid-base64-url!") })

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        verifyErrorResponse(response.params, "invalid parameters")
    }

    @Test
    fun `when encryption fails then error response is sent`() {
        configureSyncEnabled()
        configureSignedIn()
        whenever(mockCrypto.encrypt(any<ByteArray>())).thenThrow(RuntimeException("Encryption failed"))
        val jsMessage = createJsMessage(TEST_MESSAGE_ID, JSONObject().apply { put("data", BASE64_URL_DATA) })

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        verifyErrorResponse(response.params, "encryption failed")
    }

    @Test
    fun `when encryption succeeds then success response with encrypted data is sent`() {
        configureSyncEnabled()
        configureSignedIn()
        whenever(mockCrypto.encrypt(any<ByteArray>())).thenReturn(ENCRYPTED_BYTES)
        val jsMessage = createJsMessage(TEST_MESSAGE_ID, JSONObject().apply { put("data", BASE64_URL_DATA) })

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        assertEquals(TEST_MESSAGE_ID, response.id)
        assertEquals(FEATURE_NAME, response.featureName)
        assertEquals(METHOD_NAME, response.method)
        verifySuccessResponse(response.params)
    }

    private fun configureSyncEnabled() {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(true)
    }

    private fun configureSignedIn() {
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(SignedIn("testUserId", emptyList()))
    }

    private fun verifyNoResponse() {
        verifyNoInteractions(mockJsMessaging)
        verifyNoInteractions(mockCrypto)
        verifyNoInteractions(mockDeviceSyncState)
    }

    private fun verifySuccessResponse(params: JSONObject) {
        assertTrue(params.getBoolean("ok"))
        val payload = params.getJSONObject("payload")
        val encryptedData = payload.getString("encryptedData")

        // Verify it's base64Url encoded (contains - or _ instead of + or /)
        assertTrue(encryptedData.contains("-") || encryptedData.contains("_") || encryptedData.matches(Regex("[A-Za-z0-9_-]+")))

        // Verify it matches expected encrypted base64Url
        assertEquals(ENCRYPTED_BASE64_URL, encryptedData)
    }

    private fun verifyErrorResponse(params: JSONObject, expectedReason: String) {
        assertFalse(params.getBoolean("ok"))
        assertEquals(expectedReason, params.getString("reason"))
    }

    private fun createJsMessage(id: String?, params: JSONObject): JsMessage {
        return JsMessage(
            context = "test",
            featureName = FEATURE_NAME,
            method = METHOD_NAME,
            id = id,
            params = params,
        )
    }

    companion object {
        private const val TEST_MESSAGE_ID = "test-id"
        private const val FEATURE_NAME = "aiChat"
        private const val METHOD_NAME = "encryptWithSyncMasterKey"

        // Test data: "Hello" as base64Url
        private const val BASE64_URL_DATA = "SGVsbG8"

        // Encrypted bytes (mock) - just use different bytes for testing
        private val ENCRYPTED_BYTES = byteArrayOf(1, 2, 3, 4, 5)

        // Encrypted bytes as base64Url: using same utility function as production code
        private val ENCRYPTED_BASE64_URL = Base64.encodeToString(ENCRYPTED_BYTES, Base64.NO_WRAP).applyUrlSafetyFromB64()
    }
}
