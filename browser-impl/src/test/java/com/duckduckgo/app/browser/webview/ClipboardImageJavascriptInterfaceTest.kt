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

package com.duckduckgo.app.browser.webview

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardImageJavascriptInterfaceTest {

    @Test
    fun whenCopyImageToClipboardCalledThenCallbackInvokedWithCorrectParameters() {
        var capturedDataUrl: String? = null
        var capturedMimeType: String? = null

        val testee = ClipboardImageJavascriptInterface { dataUrl, mimeType ->
            capturedDataUrl = dataUrl
            capturedMimeType = mimeType
        }

        testee.copyImageToClipboard("data:image/png;base64,abc123", "image/png")

        assertEquals("data:image/png;base64,abc123", capturedDataUrl)
        assertEquals("image/png", capturedMimeType)
    }

    @Test
    fun whenCopyImageToClipboardCalledMultipleTimesThenCallbackInvokedEachTime() {
        var callCount = 0

        val testee = ClipboardImageJavascriptInterface { _, _ ->
            callCount++
        }

        testee.copyImageToClipboard("data1", "image/png")
        testee.copyImageToClipboard("data2", "image/jpeg")
        testee.copyImageToClipboard("data3", "image/webp")

        assertEquals(3, callCount)
    }

    @Test
    fun whenJavascriptInterfaceNameAccessedThenReturnsCorrectValue() {
        assertEquals("DDGClipboard", ClipboardImageJavascriptInterface.JAVASCRIPT_INTERFACE_NAME)
    }
}
