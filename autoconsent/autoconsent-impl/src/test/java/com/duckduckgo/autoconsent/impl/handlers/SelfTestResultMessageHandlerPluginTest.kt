/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.handlers

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel
import com.duckduckgo.autoconsent.impl.pixels.AutoconsentPixelManager
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@RunWith(AndroidJUnit4::class)
class SelfTestResultMessageHandlerPluginTest {

    private val mockCallback: AutoconsentCallback = mock()
    private val mockPixelManager: AutoconsentPixelManager = mock()
    private val webView: WebView = WebView(InstrumentationRegistry.getInstrumentation().targetContext)

    private val selfTestPlugin = SelfTestResultMessageHandlerPlugin(mockPixelManager)

    @Test
    fun whenProcessIfMessageTypeIsNotSelfTestThenDoNothing() {
        selfTestPlugin.process("noMatching", "", webView, mockCallback)

        verifyNoInteractions(mockCallback)
    }

    @Test
    fun whenProcessIfCannotParseMessageThenDoNothing() {
        val message = """
            {"type":"${selfTestPlugin.supportedTypes.first()}", cmp: "test", "result": true, "url": "http://example.com"}
        """.trimIndent()

        selfTestPlugin.process(selfTestPlugin.supportedTypes.first(), message, webView, mockCallback)

        verifyNoInteractions(mockCallback)
    }

    @Test
    fun whenProcessThenCallDashboardWithCorrectParameters() {
        val message = """
            {"type":"${selfTestPlugin.supportedTypes.first()}", "cmp": "test", "result": true, "url": "http://example.com"}
        """.trimIndent()

        selfTestPlugin.process(selfTestPlugin.supportedTypes.first(), message, webView, mockCallback)

        verify(mockCallback).onResultReceived(consentManaged = true, optOutFailed = false, selfTestFailed = true, isCosmetic = null)

        val anotherMessage = """
            {"type":"${selfTestPlugin.supportedTypes}", "cmp": "test", "result": false, "url": "http://example.com"}
        """.trimIndent()

        selfTestPlugin.process(selfTestPlugin.supportedTypes.first(), anotherMessage, webView, mockCallback)

        verify(mockCallback).onResultReceived(consentManaged = true, optOutFailed = false, selfTestFailed = false, isCosmetic = null)
    }

    @Test
    fun whenProcessWithResultTrueThenFireSelfTestOkPixel() {
        val message = """
            {"type":"${selfTestPlugin.supportedTypes.first()}", "cmp": "test", "result": true, "url": "http://example.com"}
        """.trimIndent()

        selfTestPlugin.process(selfTestPlugin.supportedTypes.first(), message, webView, mockCallback)

        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_SELF_TEST_OK_DAILY)
    }

    @Test
    fun whenProcessWithResultFalseThenFireSelfTestFailPixel() {
        val message = """
            {"type":"${selfTestPlugin.supportedTypes.first()}", "cmp": "test", "result": false, "url": "http://example.com"}
        """.trimIndent()

        selfTestPlugin.process(selfTestPlugin.supportedTypes.first(), message, webView, mockCallback)

        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_SELF_TEST_FAIL_DAILY)
    }
}
