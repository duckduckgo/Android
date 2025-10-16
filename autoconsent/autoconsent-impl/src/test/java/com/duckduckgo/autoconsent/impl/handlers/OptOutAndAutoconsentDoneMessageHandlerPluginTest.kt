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
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
class OptOutAndAutoconsentDoneMessageHandlerPluginTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private val mockCallback: AutoconsentCallback = mock()
    private val mockPixelManager: AutoconsentPixelManager = mock()
    private val webView: WebView = WebView(InstrumentationRegistry.getInstrumentation().targetContext)

    private val handler = OptOutAndAutoconsentDoneMessageHandlerPlugin(TestScope(), coroutineRule.testDispatcherProvider, mockPixelManager)

    @Test
    fun whenProcessIfMessageTypeIsNotIncludedInListThenDoNothing() {
        handler.process("noMatching", "", webView, mockCallback)

        verifyNoInteractions(mockCallback)
        assertNull(Shadows.shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenProcessIfCannotParseMessageThenDoNothing() {
        val message = """
            {"type":"${handler.supportedTypes}", url: "http://www.example.com", "cmp: "test"}
        """.trimIndent()

        handler.process(handler.supportedTypes.first(), message, webView, mockCallback)

        verifyNoInteractions(mockCallback)
        assertNull(Shadows.shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenProcessOptOutIfResultIsFailsThenSendResultWithFailure() {
        handler.process(getOptOut(), optOutMessage(result = false, selfTest = false), webView, mockCallback)

        verify(mockCallback).onResultReceived(consentManaged = true, optOutFailed = true, selfTestFailed = false, isCosmetic = null)
    }

    @Test
    fun whenProcessAutoconsentDoneIfCosmeticThenResultSentWithCosmeticSetToTrue() {
        handler.process(getAutoconsentType(), autoconsentDoneMessage(cosmetic = true), webView, mockCallback)

        verify(mockCallback).onResultReceived(consentManaged = true, optOutFailed = false, selfTestFailed = false, isCosmetic = true)
    }

    @Test
    fun whenProcessAutoconsentDoneIfNotCosmeticThenResultSentWithCosmeticSetToFalse() {
        handler.process(getAutoconsentType(), autoconsentDoneMessage(cosmetic = false), webView, mockCallback)

        verify(mockCallback).onResultReceived(consentManaged = true, optOutFailed = false, selfTestFailed = false, isCosmetic = false)
    }

    @Test
    fun whenProcessAutoconsentDoneIfCannotGetHostTHenDoNothing() {
        handler.process(getAutoconsentType(), autoconsentDoneMessage("noHost"), webView, mockCallback)

        verifyNoInteractions(mockCallback)
        assertNull(Shadows.shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenProcessOptOutWithSelfTestThenAutoconsentCallsEvaluateJavascript() {
        val expected = """javascript:(function() {window.autoconsentMessageCallback({ "type": "selfTest" }, window.origin);})();"""

        handler.process(getOptOut(), optOutMessage(result = true, selfTest = true), webView, mockCallback)
        handler.process(getAutoconsentType(), autoconsentDoneMessage(), webView, mockCallback)

        assertEquals(expected, Shadows.shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenProcessOptOutAndResultIsFalseThenFireErrorOptOutPixel() {
        handler.process(getOptOut(), optOutMessage(result = false, selfTest = false), webView, mockCallback)

        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_ERROR_OPTOUT_DAILY)
    }

    @Test
    fun whenProcessAutoconsentDoneAndIsCosmeticThenFireDoneCosmeticPixel() {
        handler.process(getAutoconsentType(), autoconsentDoneMessage(cosmetic = true), webView, mockCallback)

        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DONE_COSMETIC_DAILY)
    }

    @Test
    fun whenProcessAutoconsentDoneAndNotCosmeticThenFireDonePixel() {
        handler.process(getAutoconsentType(), autoconsentDoneMessage(cosmetic = false), webView, mockCallback)

        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_DONE_DAILY)
    }

    private fun getOptOut(): String = handler.supportedTypes.first()

    private fun getAutoconsentType(): String = handler.supportedTypes.last()

    private fun optOutMessage(result: Boolean, selfTest: Boolean): String {
        return """
            {"type":"${getOptOut()}", "result": $result, "scheduleSelfTest": $selfTest, "cmp": "test", "url": "http://www.example.com"}
        """.trimIndent()
    }

    private fun autoconsentDoneMessage(url: String = "http://www.example.com", cosmetic: Boolean = false): String {
        return """
            {"type":"${getAutoconsentType()}", "cmp": "test", "url": "$url", "isCosmetic": $cosmetic}
        """.trimIndent()
    }
}
