/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.email

import android.webkit.WebView
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.R
import com.nhaarman.mockitokotlin2.*
import org.junit.Before
import org.junit.Test

class EmailInjectorJsTest {

    private val mockEmailManager: EmailManager = mock()
    lateinit var testee: EmailInjectorJs

    @Before
    fun setup() {
        testee = EmailInjectorJs(mockEmailManager, DuckDuckGoUrlDetector())
    }

    @UiThreadTest
    @Test
    fun whenInjectEmailAutofillJsAndUrlIsFromDuckDuckGoDomainThenInjectJsCode() {
        val jsToEvaluate = getJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectEmailAutofillJs(webView, "https://duckduckgo.com")

        verify(webView).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    fun whenInjectEmailAutofillJsAndUrlIsFromDuckDuckGoSubdomainThenInjectJsCode() {
        val jsToEvaluate = getJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectEmailAutofillJs(webView, "https://test.duckduckgo.com")

        verify(webView).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    fun whenInjectEmailAutofillJsAndUrlIsNotFromDuckDuckGoAndEmailIsSignedInThenInjectJsCode() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
        val jsToEvaluate = getJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectEmailAutofillJs(webView, "https://example.com")

        verify(webView).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    fun whenInjectEmailAutofillJsAndUrlIsNotFromDuckDuckGoAndEmailIsNotSignedInThenDoNotInjectJsCode() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(false)
        val jsToEvaluate = getJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectEmailAutofillJs(webView, "https://example.com")

        verify(webView, never()).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    fun whenInjectEmailAutofillJsTwiceThenDoNotInjectJsCodeTwice() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
        val jsToEvaluate = getJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectEmailAutofillJs(webView, "https://example.com")
        testee.injectEmailAutofillJs(webView, "https://example.com")

        verify(webView, times(1)).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    fun whenResetInjectedFlagCalledBetweenTwoInjectEmailJsCallsThenInjectJsCodeTwice() {
        whenever(mockEmailManager.isSignedIn()).thenReturn(true)
        val jsToEvaluate = getJsToEvaluate()
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectEmailAutofillJs(webView, "https://example.com")
        testee.resetInjectedJsFlag()
        testee.injectEmailAutofillJs(webView, "https://example.com")

        verify(webView, times(2)).evaluateJavascript(jsToEvaluate, null)
    }

    @UiThreadTest
    @Test
    fun whenInjectAddressThenInjectJsCodeReplacingTheAlias() {
        val address = "address"
        val jsToEvaluate = getAliasJsToEvaluate().replace("%s", address)
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))

        testee.injectAddressInEmailField(webView, address)

        verify(webView).evaluateJavascript(jsToEvaluate, null)
    }

    private fun getJsToEvaluate(): String {
        val js = InstrumentationRegistry.getInstrumentation().targetContext.resources.openRawResource(R.raw.autofill)
            .bufferedReader()
            .use { it.readText() }
        return "javascript:$js"
    }

    private fun getAliasJsToEvaluate(): String {
        val js = InstrumentationRegistry.getInstrumentation().targetContext.resources.openRawResource(R.raw.inject_alias)
            .bufferedReader()
            .use { it.readText() }
        return "javascript:$js"
    }
}
