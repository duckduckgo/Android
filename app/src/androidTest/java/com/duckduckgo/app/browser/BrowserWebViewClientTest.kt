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
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.*
import androidx.core.net.toUri
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.certificates.rootstore.TrustedCertificateStore
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.logindetection.DOMLoginDetector
import com.duckduckgo.app.browser.logindetection.WebNavigationEvent
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.email.EmailInjector
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.privacy.config.api.Gpc
import com.nhaarman.mockitokotlin2.*
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
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
    private val gpc: Gpc = mock()
    private val trustedCertificateStore: TrustedCertificateStore = mock()
    private val webViewHttpAuthStore: WebViewHttpAuthStore = mock()
    private val thirdPartyCookieManager: ThirdPartyCookieManager = mock()
    private val emailInjector: EmailInjector = mock()
    private val webResourceRequest: WebResourceRequest = mock()

    @UiThreadTest
    @Before
    fun setup() {
        webView = TestWebView(InstrumentationRegistry.getInstrumentation().targetContext)
        testee = BrowserWebViewClient(
            webViewHttpAuthStore,
            trustedCertificateStore,
            requestRewriter,
            specialUrlDetector,
            requestInterceptor,
            offlinePixelCountDataStore,
            uncaughtExceptionRepository,
            cookieManager,
            loginDetector,
            dosDetector,
            gpc,
            thirdPartyCookieManager,
            TestCoroutineScope(),
            coroutinesTestRule.testDispatcherProvider,
            emailInjector
        )
        testee.webViewClientListener = listener
        whenever(webResourceRequest.url).thenReturn(Uri.EMPTY)
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
    fun whenOnPageStartedCalledIfUrlIsNullThenDoNotInjectGpcToDom() = coroutinesTestRule.runBlocking {
        whenever(gpc.canGpcBeUsedByUrl(any())).thenReturn(true)

        testee.onPageStarted(webView, null, null)
        verify(gpc, never()).getGpcJs()
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledIfUrlIsValidThenInjectGpcToDom() = coroutinesTestRule.runBlocking {
        whenever(gpc.canGpcBeUsedByUrl(any())).thenReturn(true)

        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(gpc).getGpcJs()
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledIfUrlIsNotAndValidThenDoNotInjectGpcToDom() = coroutinesTestRule.runBlocking {
        whenever(gpc.canGpcBeUsedByUrl(any())).thenReturn(false)

        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(gpc, never()).getGpcJs()
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenProcessUriForThirdPartyCookiesCalled() = coroutinesTestRule.runBlocking {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(thirdPartyCookieManager).processUriForThirdPartyCookies(webView, EXAMPLE_URL.toUri())
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
    fun whenOnReceivedHttpAuthRequestThrowsExceptionThenRecordException() = coroutinesTestRule.runBlocking {
        val exception = RuntimeException()
        val mockHandler = mock<HttpAuthHandler>()
        val mockWebView = mock<WebView>()
        whenever(mockWebView.url).thenThrow(exception)
        testee.onReceivedHttpAuthRequest(mockWebView, mockHandler, EXAMPLE_URL, EXAMPLE_URL)
        verify(uncaughtExceptionRepository).recordUncaughtException(exception, UncaughtExceptionSource.ON_HTTP_AUTH_REQUEST)
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

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledThenFlushCookies() {
        testee.onPageFinished(webView, null)
        verify(cookieManager).flush()
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedThrowsExceptionThenRecordException() = coroutinesTestRule.runBlocking {
        val exception = RuntimeException()
        val mockWebView: WebView = mock()
        whenever(mockWebView.url).thenThrow(exception)
        testee.onPageFinished(mockWebView, null)
        verify(uncaughtExceptionRepository).recordUncaughtException(exception, UncaughtExceptionSource.ON_PAGE_FINISHED)
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenInjectEmailAutofillJsCalled() {
        testee.onPageStarted(webView, null, null)
        verify(emailInjector).injectEmailAutofillJs(webView, null)
    }

    @Test
    fun whenOnPageStartedThrowsExceptionThenRecordException() = coroutinesTestRule.runBlocking {
        val exception = RuntimeException()
        val mockWebView: WebView = mock()
        whenever(mockWebView.url).thenThrow(exception)
        testee.onPageStarted(mockWebView, null, null)
        verify(uncaughtExceptionRepository).recordUncaughtException(exception, UncaughtExceptionSource.ON_PAGE_STARTED)
    }

    @Test
    fun whenAppLinkDetectedAndIsHandledThenReturnTrue() = coroutinesTestRule.runBlocking {
        val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL)
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(urlType)
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        whenever(listener.handleAppLink(any(), any())).thenReturn(true)
        assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).handleAppLink(urlType, isForMainFrame = true)
    }

    @Test
    fun whenAppLinkDetectedAndIsNotHandledThenReturnFalse() = coroutinesTestRule.runBlocking {
        val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL)
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(urlType)
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        whenever(listener.handleAppLink(any(), any())).thenReturn(false)
        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).handleAppLink(urlType, isForMainFrame = true)
    }

    @Test
    fun whenAppLinkDetectedAndListenerIsNullThenReturnFalse() = coroutinesTestRule.runBlocking {
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL))
        testee.webViewClientListener = null
        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener, never()).handleAppLink(any(), any())
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndIsHandledThenReturnTrue() = coroutinesTestRule.runBlocking {
        val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(urlType)
        whenever(listener.handleNonHttpAppLink(any())).thenReturn(true)
        assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).handleNonHttpAppLink(urlType)
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndIsNotHandledThenReturnFalse() = coroutinesTestRule.runBlocking {
        val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(urlType)
        whenever(listener.handleNonHttpAppLink(any())).thenReturn(false)
        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).handleNonHttpAppLink(urlType)
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndListenerIsNullThenReturnTrue() = coroutinesTestRule.runBlocking {
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL))
        testee.webViewClientListener = null
        assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener, never()).handleNonHttpAppLink(any())
    }

    private class TestWebView(context: Context) : WebView(context)

    companion object {
        const val EXAMPLE_URL = "example.com"
    }
}
