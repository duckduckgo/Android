/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.content.Context
import android.webkit.HttpAuthHandler
import android.webkit.WebView
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class BrowserWebViewClientTest {

    private lateinit var testee: BrowserWebViewClient
    private lateinit var webView: WebView

    private val requestRewriter: RequestRewriter = mock()
    private val specialUrlDetector: SpecialUrlDetector = mock()
    private val requestInterceptor: RequestInterceptor = mock()
    private val listener: WebViewClientListener = mock()

    @UiThreadTest
    @Before
    fun setup() {
        webView = TestWebView(InstrumentationRegistry.getInstrumentation().targetContext)
        testee = BrowserWebViewClient(
            requestRewriter,
            specialUrlDetector,
            requestInterceptor
        )
        testee.webViewClientListener = listener
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenListenerNotified() {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(listener).loadingStarted(EXAMPLE_URL)
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledThenListenerNotified() = runBlocking {
        testee.onPageFinished(webView, EXAMPLE_URL)
        verify(listener).loadingFinished(EXAMPLE_URL)
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledThenListenerInstructedToUpdateNavigationOptions() {
        testee.onPageFinished(webView, EXAMPLE_URL)
        verify(listener).navigationOptionsChanged(any())
    }

    @UiThreadTest
    @Test
    fun whenOnReceivedHttpAuthRequestThenListenerNotified() {
        val mockHandler = mock<HttpAuthHandler>()
        val authenticationRequest = BasicAuthenticationRequest(mockHandler, EXAMPLE_URL, EXAMPLE_URL, EXAMPLE_URL)
        testee.onReceivedHttpAuthRequest(webView, mockHandler, EXAMPLE_URL, EXAMPLE_URL)
        verify(listener).requiresAuthentication(authenticationRequest)
    }

    private class TestWebView(context: Context) : WebView(context)

    companion object {
        const val EXAMPLE_URL = "example.com"
    }
}