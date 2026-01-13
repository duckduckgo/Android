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
class GetSyncStatusHandlerTest {

    private val mockDeviceSyncState: DeviceSyncState = mock()
    private val mockSyncStore: SyncStore = mock()
    private val mockJsMessaging: JsMessaging = mock()

    val callbackDataCaptor = argumentCaptor<JsCallbackData>()

    private lateinit var handler: GetSyncStatusHandler

    @Before
    fun setUp() {
        handler = GetSyncStatusHandler(
            deviceSyncState = mockDeviceSyncState,
            syncStore = mockSyncStore,
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
        assertEquals("aiChat", handler.getJsMessageHandler().featureName)
    }

    @Test
    fun `when checking methods then returns getSyncStatus`() {
        val methods = handler.getJsMessageHandler().methods
        assertEquals(1, methods.size)
        assertEquals("getSyncStatus", methods[0])
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
    fun `when signed in and sync available then response includes user data`() {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(true)
        configureSignedIn()
        val jsMessage = createJsMessage("test-id")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        assertEquals("test-id", response.id)
        assertEquals("aiChat", response.featureName)
        assertEquals("getSyncStatus", response.method)

        val payload = response.params.getJSONObject("payload")
        verifySyncAvailable(payload)
        assertTrue(response.params.getBoolean("ok"))
    }

    @Test
    fun `when signed in and sync not available then response includes user data but syncAvailable is false`() {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(false)
        configureSignedIn()
        val jsMessage = createJsMessage("test-id")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        val payload = response.params.getJSONObject("payload")
        assertFalse(payload.getBoolean("syncAvailable"))
        assertEquals(SIGNED_IN_USER_ID, payload.getString("userId"))
        assertEquals(SIGNED_IN_DEVICE_ID, payload.getString("deviceId"))
        assertEquals(SIGNED_IN_DEVICE_NAME, payload.getString("deviceName"))
        assertEquals(DEVICE_TYPE_MOBILE, payload.getString("deviceType"))
    }

    @Test
    fun `when not signed in and sync available then response has null user data`() {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(true)
        whenever(mockSyncStore.isSignedIn()).thenReturn(false)
        val jsMessage = createJsMessage("test-id")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        val payload = response.params.getJSONObject("payload")
        assertTrue(payload.getBoolean("syncAvailable"))
        assertTrue(payload.isNull("userId"))
        assertTrue(payload.isNull("deviceId"))
        assertTrue(payload.isNull("deviceName"))
        assertTrue(payload.isNull("deviceType"))
    }

    @Test
    fun `when not signed in and sync not available then response has null user data and syncAvailable is false`() {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(false)
        whenever(mockSyncStore.isSignedIn()).thenReturn(false)
        val jsMessage = createJsMessage("test-id")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        val payload = response.params.getJSONObject("payload")
        verifySyncUnavailable(payload)
    }

    private fun configureSignedIn() {
        whenever(mockSyncStore.isSignedIn()).thenReturn(true)
        whenever(mockSyncStore.userId).thenReturn(SIGNED_IN_USER_ID)
        whenever(mockSyncStore.deviceId).thenReturn(SIGNED_IN_DEVICE_ID)
        whenever(mockSyncStore.deviceName).thenReturn(SIGNED_IN_DEVICE_NAME)
    }

    private fun verifySyncAvailable(payload: JSONObject) {
        assertTrue(payload.getBoolean("syncAvailable"))
        assertEquals(SIGNED_IN_USER_ID, payload.getString("userId"))
        assertEquals(SIGNED_IN_DEVICE_ID, payload.getString("deviceId"))
        assertEquals(SIGNED_IN_DEVICE_NAME, payload.getString("deviceName"))
        assertEquals(DEVICE_TYPE_MOBILE, payload.getString("deviceType"))
    }

    private fun verifySyncUnavailable(payload: JSONObject) {
        assertFalse(payload.getBoolean("syncAvailable"))
        assertTrue(payload.isNull("userId"))
        assertTrue(payload.isNull("deviceId"))
        assertTrue(payload.isNull("deviceName"))
        assertTrue(payload.isNull("deviceType"))
    }

    private fun verifyNoResponse() {
        verifyNoInteractions(mockJsMessaging)
        verifyNoInteractions(mockDeviceSyncState)
        verifyNoInteractions(mockSyncStore)
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

    companion object {
        private const val SIGNED_IN_USER_ID = "user123"
        private const val SIGNED_IN_DEVICE_ID = "device456"
        private const val SIGNED_IN_DEVICE_NAME = "My Device"
        private const val DEVICE_TYPE_MOBILE = "mobile"
    }
}
