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
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_USE_SUCCESS_COUNT
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_USE_SUCCESS_DAILY
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class PasskeyUsedContentScopeJsMessageHandlerTest {

    private val pixel: Pixel = mock()
    private val jsMessaging: JsMessaging = mock()
    private val testee = PasskeyUsedContentScopeJsMessageHandler(
        parser = PasskeyUsedMessageParser(),
        pixelSender = PasskeyUsagePixelSender(pixel),
    )

    @Test
    fun whenProcessPasskeyUsedGetThenFiresUseSuccessPixels() {
        testee.getJsMessageHandler().process(
            jsMessage = message(method = "passkeyUsed", type = "get"),
            jsMessaging = jsMessaging,
            jsMessageCallback = null,
        )

        verify(pixel).fire(eq(AUTOFILL_PASSKEY_USE_SUCCESS_COUNT), eq(emptyMap()), any(), eq(Count))
        verify(pixel).fire(eq(AUTOFILL_PASSKEY_USE_SUCCESS_DAILY), eq(emptyMap()), any(), eq(Daily()))
    }

    private fun message(
        method: String,
        type: String,
    ): JsMessage {
        return JsMessage(
            context = "contentScopeScripts",
            featureName = "webCompat",
            method = method,
            params = JSONObject().put("type", type),
            id = null,
        )
    }
}
