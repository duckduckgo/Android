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

package com.duckduckgo.serp.logos.impl

import android.webkit.WebView
import com.duckduckgo.serp.logos.api.SerpLogo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class RealSerpLogoEvaluatorTest {

    @Mock
    private lateinit var webView: WebView

    @Mock
    private lateinit var serpLogoJavascriptInterface: SerpLogoJavascriptInterface

    private lateinit var testee: RealSerpLogoEvaluator

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(serpLogoJavascriptInterface.js).thenReturn("mock_js_code")
        testee = RealSerpLogoEvaluator(serpLogoJavascriptInterface)
    }

    @Test
    fun whenJavaScriptReturnsEasterEggWithUrlThenReturnsEasterEggLogo() = runTest {
        val expectedUrl = "/some/path/to/logo.png"
        val jsResult = "\"easterEgg|$expectedUrl\""
        mockWebViewEvaluateJavaScript(jsResult)

        val result = testee.extractSerpLogo(webView)

        assertEquals(SerpLogo.EasterEgg("https://duckduckgo.com$expectedUrl"), result)
    }

    @Test
    fun whenJavaScriptReturnsNormalThenReturnsNormalLogo() = runTest {
        val jsResult = "\"normal|/some/path/to/normal.png\""
        mockWebViewEvaluateJavaScript(jsResult)

        val result = testee.extractSerpLogo(webView)

        assertEquals(SerpLogo.Normal, result)
    }

    @Test
    fun whenJavaScriptReturnsUnknownTypeThenReturnsNormalLogo() = runTest {
        val jsResult = "\"unknownType|/some/path/to/logo.png\""
        mockWebViewEvaluateJavaScript(jsResult)

        val result = testee.extractSerpLogo(webView)

        assertEquals(SerpLogo.Normal, result)
    }

    @Test
    fun whenJavaScriptReturnsNullThenReturnsNormalLogo() = runTest {
        val jsResult = "null"
        mockWebViewEvaluateJavaScript(jsResult)

        val result = testee.extractSerpLogo(webView)

        assertEquals(SerpLogo.Normal, result)
    }

    @Test
    fun whenJavaScriptReturnsQuotedNullThenReturnsNormalLogo() = runTest {
        val jsResult = "\"null\""
        mockWebViewEvaluateJavaScript(jsResult)

        val result = testee.extractSerpLogo(webView)

        assertEquals(SerpLogo.Normal, result)
    }

    @Test
    fun whenJavaScriptReturnsEmptyStringThenReturnsNormalLogo() = runTest {
        val jsResult = "\"\""
        mockWebViewEvaluateJavaScript(jsResult)

        val result = testee.extractSerpLogo(webView)

        assertEquals(SerpLogo.Normal, result)
    }

    @Test
    fun whenJavaScriptReturnsBlankStringThenReturnsNormalLogo() = runTest {
        val jsResult = "\"   \""
        mockWebViewEvaluateJavaScript(jsResult)

        val result = testee.extractSerpLogo(webView)

        assertEquals(SerpLogo.Normal, result)
    }

    @Test
    fun whenJavaScriptReturnsInvalidFormatThenReturnsNormalLogo() = runTest {
        val jsResult = "\"invalid_format_without_delimiter\""
        mockWebViewEvaluateJavaScript(jsResult)

        val result = testee.extractSerpLogo(webView)

        assertEquals(SerpLogo.Normal, result)
    }

    @Test
    fun whenJavaScriptReturnsOnlyDelimiterThenReturnsNormalLogo() = runTest {
        val jsResult = "\"|\""
        mockWebViewEvaluateJavaScript(jsResult)

        val result = testee.extractSerpLogo(webView)

        assertEquals(SerpLogo.Normal, result)
    }

    @Test
    fun whenJavaScriptReturnsEmptyTypeThenReturnsNormalLogo() = runTest {
        val jsResult = "\"|/some/path/to/logo.png\""
        mockWebViewEvaluateJavaScript(jsResult)

        val result = testee.extractSerpLogo(webView)

        assertEquals(SerpLogo.Normal, result)
    }

    @Test
    fun whenJavaScriptReturnsEmptyUrlForEasterEggThenReturnsEasterEgg() = runTest {
        val jsResult = "\"easterEgg|\""
        mockWebViewEvaluateJavaScript(jsResult)

        val result = testee.extractSerpLogo(webView)

        assertEquals(SerpLogo.EasterEgg("https://duckduckgo.com"), result)
    }

    @Test
    fun whenJavaScriptReturnsEasterEggWithMultiplePipesThenUsesFirstTwoPartsOnly() = runTest {
        val jsResult = "\"easterEgg|/path/to/logo.png\""
        mockWebViewEvaluateJavaScript(jsResult)

        val result = testee.extractSerpLogo(webView)

        assertEquals(SerpLogo.EasterEgg("https://duckduckgo.com/path/to/logo.png"), result)
    }

    private fun mockWebViewEvaluateJavaScript(result: String) {
        doAnswer { invocation ->
            val callback = invocation.arguments[1] as android.webkit.ValueCallback<String>
            callback.onReceiveValue(result)
            null
        }.whenever(webView).evaluateJavascript(any(), any())
    }
}
