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

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.impl.DuckChatConstants.HOST_DUCK_AI
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.api.DeviceSyncState.SyncAccountState.SignedIn
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SetUpSyncHandlerTest {

    private val mockGlobalActivityStarter: GlobalActivityStarter = mock()
    private val mockContext: Context = mock()
    private val mockDeviceSyncState: DeviceSyncState = mock()
    private val mockJsMessaging: JsMessaging = mock()

    val callbackDataCaptor = argumentCaptor<JsCallbackData>()

    private lateinit var handler: SetUpSyncHandler

    @Before
    fun setUp() {
        handler = SetUpSyncHandler(
            globalActivityStarter = mockGlobalActivityStarter,
            context = mockContext,
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
        assertEquals("aiChat", handler.getJsMessageHandler().featureName)
    }

    @Test
    fun `when checking methods then returns sendToSyncSettings and sendToSetupSync`() {
        val methods = handler.getJsMessageHandler().methods
        assertEquals(2, methods.size)
        assertEquals("sendToSyncSettings", methods[0])
        assertEquals("sendToSetupSync", methods[1])
    }

    @Test
    fun `when id is null then activity is not started`() {
        val jsMessage = createJsMessage("sendToSyncSettings", null)

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verifyNoInteractions(mockGlobalActivityStarter)
        verifyNoInteractions(mockContext)
        verifyNoInteractions(mockJsMessaging)
    }

    @Test
    fun `when id is empty then activity is not started`() {
        val jsMessage = createJsMessage("sendToSyncSettings", "")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verifyNoInteractions(mockGlobalActivityStarter)
        verifyNoInteractions(mockContext)
        verifyNoInteractions(mockJsMessaging)
    }

    @Test
    fun `when sendToSetupSync and sync feature is disabled then error response is sent`() {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(false)
        val jsMessage = createJsMessage("sendToSetupSync", "test-id")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        assertEquals("test-id", response.id)
        assertEquals("aiChat", response.featureName)
        assertEquals("sendToSetupSync", response.method)
        verifyErrorResponse(response.params, "setup unavailable")
        verifyNoInteractions(mockGlobalActivityStarter)
        verifyNoInteractions(mockContext)
    }

    @Test
    fun `when sendToSyncSettings and sync feature is disabled then error response is sent`() {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(false)
        val jsMessage = createJsMessage("sendToSyncSettings", "test-id")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        assertEquals("test-id", response.id)
        assertEquals("aiChat", response.featureName)
        assertEquals("sendToSyncSettings", response.method)
        verifyErrorResponse(response.params, "setup unavailable")
        verifyNoInteractions(mockGlobalActivityStarter)
        verifyNoInteractions(mockContext)
    }

    @Test
    fun `when sync is already set up then error response is sent`() {
        configureSyncEnabled()
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(SignedIn("testUserId", emptyList()))
        val jsMessage = createJsMessage("sendToSetupSync", "test-id")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())
        val response = callbackDataCaptor.firstValue
        verifyErrorResponse(response.params, "sync already on")
        verifyNoInteractions(mockGlobalActivityStarter)
        verifyNoInteractions(mockContext)
    }

    @Test
    fun `when id is present and startIntent returns intent then activity is started with new task flag`() {
        configureSyncEnabled()
        configureSignedOut()
        val intent = Intent()
        whenever(mockGlobalActivityStarter.startIntent(any(), any<ActivityParams>())).thenReturn(intent)
        val jsMessage = createJsMessage("sendToSyncSettings", "test-id")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockGlobalActivityStarter).startIntent(mockContext, SyncActivityWithEmptyParams)
        assertTrue("FLAG_ACTIVITY_NEW_TASK should be set", intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        verify(mockContext).startActivity(intent)
        verifyNoInteractions(mockJsMessaging)
    }

    @Test
    fun `when id is present and startIntent returns null then activity is not started`() {
        configureSyncEnabled()
        configureSignedOut()
        whenever(mockGlobalActivityStarter.startIntent(any(), any<ActivityParams>())).thenReturn(null)
        val jsMessage = createJsMessage("sendToSyncSettings", "test-id")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockGlobalActivityStarter).startIntent(mockContext, SyncActivityWithEmptyParams)
        verify(mockContext, never()).startActivity(any())
        verifyNoInteractions(mockJsMessaging)
    }

    @Test
    fun `when sendToSetupSync method is called then activity is started`() {
        configureSyncEnabled()
        configureSignedOut()
        val intent = Intent()
        whenever(mockGlobalActivityStarter.startIntent(any(), any<ActivityParams>())).thenReturn(intent)
        val jsMessage = createJsMessage("sendToSetupSync", "test-id")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockGlobalActivityStarter).startIntent(mockContext, SyncActivityWithEmptyParams)
        verify(mockContext).startActivity(intent)
        verifyNoInteractions(mockJsMessaging)
    }

    @Test
    fun `when sendToSyncSettings and sync is already enabled then activity is still started`() {
        configureSyncEnabled()
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(SignedIn("testUserId", emptyList()))
        val intent = Intent()
        whenever(mockGlobalActivityStarter.startIntent(any(), any<ActivityParams>())).thenReturn(intent)
        val jsMessage = createJsMessage("sendToSyncSettings", "test-id")

        handler.getJsMessageHandler().process(jsMessage, mockJsMessaging, null)

        verify(mockGlobalActivityStarter).startIntent(mockContext, SyncActivityWithEmptyParams)
        verify(mockContext).startActivity(intent)
        verifyNoInteractions(mockJsMessaging)
    }

    private fun configureSyncEnabled() {
        whenever(mockDeviceSyncState.isFeatureEnabled()).thenReturn(true)
    }

    private fun configureSignedOut() {
        whenever(mockDeviceSyncState.getAccountState()).thenReturn(DeviceSyncState.SyncAccountState.SignedOut)
    }

    private fun verifyErrorResponse(params: JSONObject, expectedReason: String) {
        assertFalse(params.getBoolean("ok"))
        assertEquals(expectedReason, params.getString("reason"))
    }

    private fun createJsMessage(method: String, id: String?): JsMessage {
        return JsMessage(
            context = "test",
            featureName = "aiChat",
            method = method,
            id = id,
            params = JSONObject(),
        )
    }
}
