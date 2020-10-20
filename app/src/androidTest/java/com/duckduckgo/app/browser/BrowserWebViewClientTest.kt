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
import android.webkit.*
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.logindetection.DOMLoginDetector
import com.duckduckgo.app.browser.logindetection.WebNavigationEvent
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.globalprivacycontrol.GlobalPrivacyControlInjector
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class BrowserWebViewClientTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: BrowserWebViewClient
    private lateinit var webView: WebView

    private val requestRewriter: RequestRewriter = mock()
    private val specialUrlDetector: SpecialUrlDetector = mock()
    private val requestInterceptor: RequestInterceptor = mock()
    private val listener: WebViewClientListener = mock()
    private val cookieManager: CookieManager = mock()
    private val loginDetector: DOMLoginDetector = mock()
    private val offlinePixelCountDataStore: OfflinePixelCountDataStore = mock()
    private val uncaughtExceptionRepository: UncaughtExceptionRepository = mock()
    private val dosDetector: DosDetector = DosDetector()
    private val globalPrivacyControlInjector: GlobalPrivacyControlInjector = mock()

    @UiThreadTest
    @Before
    fun setup() {
        webView = TestWebView(InstrumentationRegistry.getInstrumentation().targetContext)
        testee = BrowserWebViewClient(
            requestRewriter,
            specialUrlDetector,
            requestInterceptor,
            offlinePixelCountDataStore,
            uncaughtExceptionRepository,
            cookieManager,
            loginDetector,
            dosDetector,
            globalPrivacyControlInjector
        )
        testee.webViewClientListener = listener
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenListenerInstructedToUpdateNavigationState() {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(listener).navigationStateChanged(any())
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledWithSameUrlAsPreviousThenListenerNotifiedOfRefresh() {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(listener).pageRefreshed(EXAMPLE_URL)
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledWithDifferentUrlToPreviousThenListenerNotNotifiedOfRefresh() {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        testee.onPageStarted(webView, "foo.com", null)
        verify(listener, never()).pageRefreshed(any())
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenEventSentToLoginDetector() = coroutinesTestRule.runBlocking {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(loginDetector).onEvent(WebNavigationEvent.OnPageStarted(webView))
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenInjectDoNotSellToDom() = coroutinesTestRule.runBlocking {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(globalPrivacyControlInjector).injectDoNotSellToDom(webView)
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledThenListenerInstructedToUpdateNavigationState() {
        testee.onPageFinished(webView, EXAMPLE_URL)
        verify(listener).navigationStateChanged(any())
    }

    @UiThreadTest
    @Test
    fun whenOnReceivedHttpAuthRequestThenListenerNotified() {
        val mockHandler = mock<HttpAuthHandler>()
        val authenticationRequest = BasicAuthenticationRequest(mockHandler, EXAMPLE_URL, EXAMPLE_URL, EXAMPLE_URL)
        testee.onReceivedHttpAuthRequest(webView, mockHandler, EXAMPLE_URL, EXAMPLE_URL)
        verify(listener).requiresAuthentication(authenticationRequest)
    }

    @UiThreadTest
    @Test
    fun whenShouldInterceptRequestThenEventSentToLoginDetector() = coroutinesTestRule.runBlocking {
        val webResourceRequest = mock<WebResourceRequest>()
        testee.shouldInterceptRequest(webView, webResourceRequest)
        verify(loginDetector).onEvent(WebNavigationEvent.ShouldInterceptRequest(webView, webResourceRequest))
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun whenRenderProcessGoneDueToCrashThenCrashDataStoreEntryIsIncremented() {
        val detail: RenderProcessGoneDetail = mock()
        whenever(detail.didCrash()).thenReturn(true)
        testee.onRenderProcessGone(webView, detail)
        verify(offlinePixelCountDataStore, times(1)).webRendererGoneCrashCount = 1
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun whenRenderProcessGoneDueToNonCrashThenOtherDataStoreEntryIsIncremented() {
        val detail: RenderProcessGoneDetail = mock()
        whenever(detail.didCrash()).thenReturn(false)
        testee.onRenderProcessGone(webView, detail)
        verify(offlinePixelCountDataStore, times(1)).webRendererGoneKilledCount = 1
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun whenRenderProcessGoneThenEmitEventIntoListener() {
        val detail: RenderProcessGoneDetail = mock()
        whenever(detail.didCrash()).thenReturn(true)
        testee.onRenderProcessGone(webView, detail)
        verify(listener, times(1)).recoverFromRenderProcessGone()
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledThenPrefetchIconCalled() {
        testee.onPageFinished(webView, EXAMPLE_URL)
        verify(listener).prefetchFavicon(EXAMPLE_URL)
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledIfUrlIsNullThenDoNotCallPrefetchIcon() {
        testee.onPageFinished(webView, null)
        verify(listener, never()).prefetchFavicon(any())
    }

    private class TestWebView(context: Context) : WebView(context)

    companion object {
        const val EXAMPLE_URL = "example.com"
    }
}
