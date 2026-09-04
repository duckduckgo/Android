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

package com.duckduckgo.autofill.impl.passkey

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

// Robolectric so the temporary android.util.Log diagnostic in the handlers resolves.
@RunWith(AndroidJUnit4::class)
class PasskeyUsedContentScopeJsMessageHandlerTest {

    private val logger: PasskeyUsedMessageLogger = mock()
    private val jsMessaging: JsMessaging = mock()

    @Test
    fun whenClassicHandlerThenRegistersWebCompatPasskeyMessagesForAllDomains() {
        val handler = PasskeyUsedContentScopeJsMessageHandler(logger).getJsMessageHandler()

        assertEquals("webCompat", handler.featureName)
        assertEquals(listOf("passkeyUsed", "passkeyFailed"), handler.methods)
        assertTrue(handler.allowedDomains.isEmpty())
    }

    @Test
    fun whenClassicHandlerReceivesPasskeyUsedThenLogUsedIsCalled() {
        val handler = PasskeyUsedContentScopeJsMessageHandler(logger).getJsMessageHandler()
        val params = usedParams()

        handler.process(message("passkeyUsed", params), jsMessaging, null)

        verify(logger).logUsed(params)
    }

    @Test
    fun whenClassicHandlerReceivesPasskeyFailedThenLogFailedIsCalled() {
        val handler = PasskeyUsedContentScopeJsMessageHandler(logger).getJsMessageHandler()
        val params = failedParams()

        handler.process(message("passkeyFailed", params), jsMessaging, null)

        verify(logger).logFailed(params)
    }

    @Test
    fun whenWebViewCompatHandlerThenRegistersWebCompatPasskeyMessages() {
        val handler = WebViewCompatPasskeyUsedContentScopeJsMessageHandler(logger).getJsMessageHandler()

        assertEquals("webCompat", handler.featureName)
        assertEquals(listOf("passkeyUsed", "passkeyFailed"), handler.methods)
    }

    @Test
    fun whenWebViewCompatHandlerReceivesPasskeyUsedThenLogUsedIsCalledAndMessageConsumed() {
        val handler = WebViewCompatPasskeyUsedContentScopeJsMessageHandler(logger).getJsMessageHandler()
        val params = usedParams()

        val result = handler.process(message("passkeyUsed", params))

        assertNull(result)
        verify(logger).logUsed(params)
    }

    @Test
    fun whenWebViewCompatHandlerReceivesPasskeyFailedThenLogFailedIsCalledAndMessageConsumed() {
        val handler = WebViewCompatPasskeyUsedContentScopeJsMessageHandler(logger).getJsMessageHandler()
        val params = failedParams()

        val result = handler.process(message("passkeyFailed", params))

        assertNull(result)
        verify(logger).logFailed(params)
    }

    private fun usedParams(): JSONObject = JSONObject().put("type", "get")

    private fun failedParams(): JSONObject = JSONObject()
        .put("type", "get")
        .put("error", "NotAllowedError")

    private fun message(
        method: String,
        params: JSONObject,
    ) = JsMessage(
        context = "contentScopeScripts",
        featureName = "webCompat",
        method = method,
        params = params,
        id = null,
    )
}
