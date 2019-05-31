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

@file:Suppress("RemoveExplicitTypeArguments")

package com.duckduckgo.app.browser

import android.content.Context
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.junit.Before
import org.junit.Test

class BrowserChromeClientTest {

    private lateinit var testee: BrowserChromeClient
    private lateinit var webView: TestWebView
    private lateinit var mockWebViewClientListener: WebViewClientListener
    private val fakeView = View(getInstrumentation().targetContext)

    @UiThreadTest
    @Before
    fun setup() {
        testee = BrowserChromeClient()
        mockWebViewClientListener = mock()
        testee.webViewClientListener = mockWebViewClientListener
        webView = TestWebView(getInstrumentation().targetContext)
    }

    @Test
    fun whenCustomViewShownForFirstTimeListenerInstructedToGoFullScreen() {
        testee.onShowCustomView(fakeView, null)
        verify(mockWebViewClientListener).goFullScreen(fakeView)
    }

    @Test
    fun whenCustomViewShownMultipleTimesListenerInstructedToGoFullScreenOnlyOnce() {
        testee.onShowCustomView(fakeView, null)
        testee.onShowCustomView(fakeView, null)
        testee.onShowCustomView(fakeView, null)
        verify(mockWebViewClientListener, times(1)).goFullScreen(fakeView)
    }

    @Test
    fun whenCustomViewShownMultipleTimesCallbackInstructedToHideForAllButTheFirstCall() {
        val mockCustomViewCallback: WebChromeClient.CustomViewCallback = mock()
        testee.onShowCustomView(fakeView, mockCustomViewCallback)
        testee.onShowCustomView(fakeView, mockCustomViewCallback)
        testee.onShowCustomView(fakeView, mockCustomViewCallback)
        verify(mockCustomViewCallback, times(2)).onCustomViewHidden()
    }

    @Test
    fun whenHideCustomViewCalledThenListenerInstructedToExistFullScreen() {
        testee.onHideCustomView()
        verify(mockWebViewClientListener).exitFullScreen()
    }

    @UiThreadTest
    @Test
    fun whenOnProgressChangedCalledThenListenerInstructedToUpdateProgressAndNavigationOptions() {
        testee.onProgressChanged(webView, 10)
        verify(mockWebViewClientListener).progressChanged(webView.stubUrl, 10)
        verify(mockWebViewClientListener).navigationOptionsChanged(any())
    }

    @UiThreadTest
    @Test
    fun whenOnProgressChangedCalledButNoUrlChangeThenListenerInstructedToUpdateProgressASecondTime() {
        webView.stubUrl = "foo.com"
        testee.onProgressChanged(webView, 10)
        testee.onProgressChanged(webView, 20)
        verify(mockWebViewClientListener, times(2)).progressChanged(any(), any())
    }

    @UiThreadTest
    @Test
    fun whenOnProgressChangedCalledAfterUrlChangeThenListenerInstructedToUpdateProgressAgain() {
        webView.stubUrl = "foo.com"
        testee.onProgressChanged(webView, 10)
        testee.onProgressChanged(webView, 20)
        webView.stubUrl = "bar.com"
        testee.onProgressChanged(webView, 30)
        verify(mockWebViewClientListener, times(3)).progressChanged(any(), any())
    }

    @UiThreadTest
    @Test
    fun whenOnProgressChangedCalledThenPassedOnToWebClient() {
        val url = "https://example.com"
        webView.stubUrl = url
        testee.onProgressChanged(webView, 10)
        verify(mockWebViewClientListener).progressChanged(url, 10)
    }

    private class TestWebView(context: Context) : WebView(context) {
        var stubUrl: String = ""

        override fun getUrl(): String {
            return stubUrl
        }
    }
}