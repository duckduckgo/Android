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

package com.duckduckgo.eventhub.impl

import com.duckduckgo.eventhub.impl.pixels.EventHubPixelManager
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class EventHubContentScopeJsMessageHandlerTest {

    private val pixelManager: EventHubPixelManager = mock()
    private val jsMessaging: JsMessaging = mock()

    private val handler = EventHubContentScopeJsMessageHandler(pixelManager = pixelManager)

    @Test
    fun `handler feature name is webEvents`() {
        assertEquals("webEvents", handler.getJsMessageHandler().featureName)
    }

    @Test
    fun `handler methods contains webEvent`() {
        val methods = handler.getJsMessageHandler().methods
        assertEquals(1, methods.size)
        assertEquals("webEvent", methods[0])
    }

    @Test
    fun `handler allowedDomains is empty`() {
        assertTrue(handler.getJsMessageHandler().allowedDomains.isEmpty())
    }

    @Test
    fun `process calls handleWebEvent with params and webViewId`() {
        val params = JSONObject().apply {
            put("type", "click")
            put("nativeData", JSONObject().apply { put("webViewId", "123") })
        }
        val jsMessage = JsMessage(
            context = "test",
            featureName = "webEvents",
            method = "webEvent",
            params = params,
            id = null,
        )

        handler.getJsMessageHandler().process(jsMessage, jsMessaging, null)

        val paramsCaptor = argumentCaptor<JSONObject>()
        val webViewIdCaptor = argumentCaptor<String>()
        verify(pixelManager).handleWebEvent(paramsCaptor.capture(), webViewIdCaptor.capture())
        assertEquals(params, paramsCaptor.firstValue)
        assertEquals("123", webViewIdCaptor.firstValue)
    }

    @Test
    fun `process extracts webViewId from nativeData`() {
        val params = JSONObject().apply {
            put("type", "scroll")
            put("nativeData", JSONObject().apply { put("webViewId", "webview-42") })
        }
        val jsMessage = JsMessage(
            context = "test",
            featureName = "webEvents",
            method = "webEvent",
            params = params,
            id = null,
        )

        handler.getJsMessageHandler().process(jsMessage, jsMessaging, null)

        val webViewIdCaptor = argumentCaptor<String>()
        verify(pixelManager).handleWebEvent(any(), webViewIdCaptor.capture())
        assertEquals("webview-42", webViewIdCaptor.firstValue)
    }

    @Test
    fun `process uses empty webViewId when nativeData is missing`() {
        val params = JSONObject().apply { put("type", "click") }
        val jsMessage = JsMessage(
            context = "test",
            featureName = "webEvents",
            method = "webEvent",
            params = params,
            id = null,
        )

        handler.getJsMessageHandler().process(jsMessage, jsMessaging, null)

        val webViewIdCaptor = argumentCaptor<String>()
        verify(pixelManager).handleWebEvent(any(), webViewIdCaptor.capture())
        assertEquals("", webViewIdCaptor.firstValue)
    }

    @Test
    fun `process does not call handleWebEvent when type is empty`() {
        val params = JSONObject().apply { put("type", "") }
        val jsMessage = JsMessage(
            context = "test",
            featureName = "webEvents",
            method = "webEvent",
            params = params,
            id = null,
        )

        handler.getJsMessageHandler().process(jsMessage, jsMessaging, null)

        verify(pixelManager, never()).handleWebEvent(any(), any())
    }

    @Test
    fun `process does not call handleWebEvent when type is missing`() {
        val params = JSONObject()
        val jsMessage = JsMessage(
            context = "test",
            featureName = "webEvents",
            method = "webEvent",
            params = params,
            id = null,
        )

        handler.getJsMessageHandler().process(jsMessage, jsMessaging, null)

        verify(pixelManager, never()).handleWebEvent(any(), any())
    }
}
