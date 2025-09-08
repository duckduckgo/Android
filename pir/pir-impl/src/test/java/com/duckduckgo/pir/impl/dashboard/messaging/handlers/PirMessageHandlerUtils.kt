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

package com.duckduckgo.pir.impl.dashboard.messaging.handlers

import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebConstants
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify

object PirMessageHandlerUtils {

    fun createJsMessage(
        paramsJson: String?,
        method: PirDashboardWebMessages,
    ): JsMessage {
        val params = if (paramsJson != null) {
            try {
                JSONObject(paramsJson)
            } catch (e: Exception) {
                JSONObject()
            }
        } else {
            JSONObject()
        }

        return JsMessage(
            context = PirDashboardWebConstants.SCRIPT_CONTEXT_NAME,
            featureName = PirDashboardWebConstants.SCRIPT_FEATURE_NAME,
            method = method.messageName,
            id = "123",
            params = params,
        )
    }

    fun verifyResponse(
        jsMessage: JsMessage,
        success: Boolean,
        mockJsMessaging: JsMessaging,
    ) {
        val callbackDataCaptor = argumentCaptor<JsCallbackData>()
        verify(mockJsMessaging).onResponse(callbackDataCaptor.capture())

        val callbackData = callbackDataCaptor.firstValue
        assertEquals(jsMessage.featureName, callbackData.featureName)
        assertEquals(jsMessage.method, callbackData.method)
        assertEquals(jsMessage.id ?: "", callbackData.id)

        // Verify the response JSON contains expected success response
        assertTrue(callbackData.params.has("success"))
        assertEquals(success, callbackData.params.getBoolean("success"))
        assertTrue(callbackData.params.has("version"))
        assertEquals(PirDashboardWebConstants.SCRIPT_API_VERSION, callbackData.params.getInt("version"))
    }
}
