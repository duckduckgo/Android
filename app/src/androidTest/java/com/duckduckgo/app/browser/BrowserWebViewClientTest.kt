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
import android.os.Build
import android.support.test.InstrumentationRegistry
import android.support.test.annotation.UiThreadTest
import android.support.test.filters.SdkSuppress
import android.webkit.WebView
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import org.junit.Before
import org.junit.Test

class BrowserWebViewClientTest {

    private lateinit var testee: BrowserWebViewClient
    private lateinit var webView: WebView

    private val requestRewriter: RequestRewriter = mock()
    private val specialUrlDetector: SpecialUrlDetector = mock()
    private val requestInterceptor: RequestInterceptor = mock()
    private val httpsUpgrader: HttpsUpgrader = mock()
    private val statisticsDataStore: StatisticsDataStore = mock()
    private val pixel: Pixel = mock()
    private val listener: WebViewClientListener = mock()

    @UiThreadTest
    @Before
    fun setup() {
        webView = TestWebView(InstrumentationRegistry.getTargetContext())

        testee = BrowserWebViewClient(requestRewriter, specialUrlDetector, requestInterceptor, httpsUpgrader, statisticsDataStore, pixel)
        testee.webViewClientListener = listener
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenListenerNotified() {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(listener).loadingStarted()
    }

    @UiThreadTest
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.M)
    fun whenOnPageStartedCalledOnNewerDevicesThenListenerNotInstructedToUpdateUrl() {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(listener, never()).urlChanged(any())
    }

    @UiThreadTest
    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.LOLLIPOP_MR1)
    fun whenOnPageStartedCalledOnOlderDevicesThenListenerInstructedToUpdateUrl() {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(listener).urlChanged(any())
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.M)
    @UiThreadTest
    @Test
    fun whenOnPageCommitVisibleCalledThenListenerInstructedToUpdateUrl() {
        testee.onPageCommitVisible(webView, EXAMPLE_URL)
        verify(listener).urlChanged(EXAMPLE_URL)
    }

    @UiThreadTest
    @Test
    fun whenOnPageCommitVisibleCalledThenListenerInstructedToUpdateNavigationOptions() {
        testee.onPageCommitVisible(webView, EXAMPLE_URL)
        verify(listener).navigationOptionsChanged(any())
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledThenListenerNotified() {
        testee.onPageFinished(webView, EXAMPLE_URL)
        verify(listener).loadingFinished(EXAMPLE_URL)
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledThenListenerInstructedToUpdateNavigationOptions() {
        testee.onPageFinished(webView, EXAMPLE_URL)
        verify(listener).navigationOptionsChanged(any())
    }

    private class TestWebView(context: Context) : WebView(context)

    companion object {
        const val EXAMPLE_URL = "example.com"
    }
}