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
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.accessibility.AccessibilityManager
import com.duckduckgo.app.browser.certificates.rootstore.TrustedCertificateStore
import com.duckduckgo.app.browser.cookies.CookieManagerProvider
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.logindetection.DOMLoginDetector
import com.duckduckgo.app.browser.logindetection.WebNavigationEvent
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.email.EmailInjector
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.AmpLinks
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString

@ExperimentalCoroutinesApi
class BrowserWebViewClientTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: BrowserWebViewClient
    private lateinit var webView: WebView

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val requestRewriter: RequestRewriter = mock()
    private val specialUrlDetector: SpecialUrlDetector = mock()
    private val requestInterceptor: RequestInterceptor = mock()
    private val listener: WebViewClientListener = mock()
    private val cookieManagerProvider: CookieManagerProvider = mock()
    private val cookieManager: CookieManager = mock()
    private val loginDetector: DOMLoginDetector = mock()
    private val offlinePixelCountDataStore: OfflinePixelCountDataStore = mock()
    private val uncaughtExceptionRepository: UncaughtExceptionRepository = mock()
    private val dosDetector: DosDetector = DosDetector()
    private val accessibilitySettings: AccessibilityManager = mock()
    private val gpc: Gpc = mock()
    private val trustedCertificateStore: TrustedCertificateStore = mock()
    private val webViewHttpAuthStore: WebViewHttpAuthStore = mock()
    private val thirdPartyCookieManager: ThirdPartyCookieManager = mock()
    private val emailInjector: EmailInjector = mock()
    private val webResourceRequest: WebResourceRequest = mock()
    private val ampLinks: AmpLinks = mock()

    @UiThreadTest
    @Before
    fun setup() {
        webView = TestWebView(context)
        testee = BrowserWebViewClient(
            webViewHttpAuthStore,
            trustedCertificateStore,
            requestRewriter,
            specialUrlDetector,
            requestInterceptor,
            offlinePixelCountDataStore,
            uncaughtExceptionRepository,
            cookieManagerProvider,
            loginDetector,
            dosDetector,
            gpc,
            thirdPartyCookieManager,
            TestScope(),
            coroutinesTestRule.testDispatcherProvider,
            emailInjector,
            accessibilitySettings,
            ampLinks
        )
        testee.webViewClientListener = listener
        whenever(webResourceRequest.url).thenReturn(Uri.EMPTY)
        whenever(cookieManagerProvider.get()).thenReturn(cookieManager)
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
    fun whenOnPageStartedCalledThenEventSentToLoginDetector() = runTest {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(loginDetector).onEvent(WebNavigationEvent.OnPageStarted(webView))
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledIfUrlIsNullThenDoNotInjectGpcToDom() = runTest {
        whenever(gpc.canGpcBeUsedByUrl(any())).thenReturn(true)

        testee.onPageStarted(webView, null, null)
        verify(gpc, never()).getGpcJs()
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledIfUrlIsValidThenInjectGpcToDom() = runTest {
        whenever(gpc.canGpcBeUsedByUrl(any())).thenReturn(true)

        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(gpc).getGpcJs()
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledIfUrlIsNotAndValidThenDoNotInjectGpcToDom() = runTest {
        whenever(gpc.canGpcBeUsedByUrl(any())).thenReturn(false)

        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(gpc, never()).getGpcJs()
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenProcessUriForThirdPartyCookiesCalled() = runTest {
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
    fun whenOnReceivedHttpAuthRequestThrowsExceptionThenRecordException() = runTest {
        val exception = RuntimeException()
        val mockHandler = mock<HttpAuthHandler>()
        val mockWebView = mock<WebView>()
        whenever(mockWebView.url).thenThrow(exception)
        testee.onReceivedHttpAuthRequest(mockWebView, mockHandler, EXAMPLE_URL, EXAMPLE_URL)
        verify(uncaughtExceptionRepository).recordUncaughtException(exception, UncaughtExceptionSource.ON_HTTP_AUTH_REQUEST)
    }

    @UiThreadTest
    @Test
    fun whenShouldInterceptRequestThenEventSentToLoginDetector() = runTest {
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
    fun whenOnPageFinishedThrowsExceptionThenRecordException() = runTest {
        val exception = RuntimeException()
        val mockWebView: WebView = mock()
        whenever(mockWebView.url).thenThrow(exception)
        testee.onPageFinished(mockWebView, null)
        verify(uncaughtExceptionRepository).recordUncaughtException(exception, UncaughtExceptionSource.ON_PAGE_FINISHED)
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedThenNotifyAccessibilityManager() {
        testee.onPageFinished(webView, "http://example.com")

        verify(accessibilitySettings).onPageFinished(webView, "http://example.com")
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenInjectEmailAutofillJsCalled() {
        testee.onPageStarted(webView, null, null)
        verify(emailInjector).injectEmailAutofillJs(webView, null)
    }

    @Test
    fun whenOnPageStartedThrowsExceptionThenRecordException() = runTest {
        val exception = RuntimeException()
        val mockWebView: WebView = mock()
        whenever(mockWebView.url).thenThrow(exception)
        testee.onPageStarted(mockWebView, null, null)
        verify(uncaughtExceptionRepository).recordUncaughtException(exception, UncaughtExceptionSource.ON_PAGE_STARTED)
    }

    @Test
    fun whenShouldOverrideThrowsExceptionThenRecordException() = runTest {
        val exception = RuntimeException()
        whenever(specialUrlDetector.determineType(any<Uri>())).thenThrow(exception)
        testee.shouldOverrideUrlLoading(webView, "")
        verify(uncaughtExceptionRepository).recordUncaughtException(exception, UncaughtExceptionSource.SHOULD_OVERRIDE_REQUEST)
    }

    @Test
    fun whenAppLinkDetectedAndIsHandledThenReturnTrue() = runTest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL)
            whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(urlType)
            whenever(webResourceRequest.isRedirect).thenReturn(false)
            whenever(webResourceRequest.isForMainFrame).thenReturn(true)
            whenever(listener.handleAppLink(any(), any())).thenReturn(true)
            assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
            verify(listener).handleAppLink(urlType, isForMainFrame = true)
        }
    }

    @Test
    fun whenAppLinkDetectedAndIsNotHandledThenReturnFalse() = runTest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL)
            whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(urlType)
            whenever(webResourceRequest.isRedirect).thenReturn(false)
            whenever(webResourceRequest.isForMainFrame).thenReturn(true)
            whenever(listener.handleAppLink(any(), any())).thenReturn(false)
            assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
            verify(listener).handleAppLink(urlType, isForMainFrame = true)
        }
    }

    @Test
    fun whenAppLinkDetectedAndListenerIsNullThenReturnFalse() = runTest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL))
            testee.webViewClientListener = null
            assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
            verify(listener, never()).handleAppLink(any(), any())
        }
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndIsHandledThenReturnTrue() = runTest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
            whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(urlType)
            whenever(webResourceRequest.isRedirect).thenReturn(false)
            whenever(listener.handleNonHttpAppLink(any())).thenReturn(true)
            assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
            verify(listener).handleNonHttpAppLink(urlType)
        }
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndIsHandledOnApiLessThan24ThenReturnTrue() = runTest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
            whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(urlType)
            whenever(listener.handleNonHttpAppLink(any())).thenReturn(true)
            assertTrue(testee.shouldOverrideUrlLoading(webView, EXAMPLE_URL))
            verify(listener).handleNonHttpAppLink(urlType)
        }
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndIsNotHandledThenReturnFalse() = runTest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
            whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(urlType)
            whenever(webResourceRequest.isRedirect).thenReturn(false)
            whenever(listener.handleNonHttpAppLink(any())).thenReturn(false)
            assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
            verify(listener).handleNonHttpAppLink(urlType)
        }
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndIsNotHandledOnApiLessThan24ThenReturnFalse() = runTest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
            whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(urlType)
            whenever(listener.handleNonHttpAppLink(any())).thenReturn(false)
            assertFalse(testee.shouldOverrideUrlLoading(webView, EXAMPLE_URL))
            verify(listener).handleNonHttpAppLink(urlType)
        }
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndListenerIsNullThenReturnTrue() = runTest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(
                SpecialUrlDetector.UrlType.NonHttpAppLink(
                    EXAMPLE_URL,
                    Intent(),
                    EXAMPLE_URL
                )
            )
            testee.webViewClientListener = null
            assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
            verify(listener, never()).handleNonHttpAppLink(any())
        }
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndListenerIsNullOnApiLessThan24ThenReturnTrue() = runTest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(
                SpecialUrlDetector.UrlType.NonHttpAppLink(
                    EXAMPLE_URL,
                    Intent(),
                    EXAMPLE_URL
                )
            )
            testee.webViewClientListener = null
            assertTrue(testee.shouldOverrideUrlLoading(webView, EXAMPLE_URL))
            verify(listener, never()).handleNonHttpAppLink(any())
        }
    }

    @Test
    fun whenAmpLinkDetectedAndIsForMainFrameThenReturnTrueAndLoadExtractedUrl() = runTest {
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(SpecialUrlDetector.UrlType.ExtractedAmpLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        val mockWebView = mock<WebView>()
        assertTrue(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView).loadUrl(EXAMPLE_URL)
        verify(listener).startProcessingTrackingLink()
    }

    @Test
    fun whenAmpLinkDetectedAndIsNotForMainFrameThenReturnFalse() = runTest {
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(SpecialUrlDetector.UrlType.ExtractedAmpLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(false)
        val mockWebView = mock<WebView>()
        assertFalse(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView, never()).loadUrl(EXAMPLE_URL)
        verify(listener, never()).startProcessingTrackingLink()
    }

    @Test
    fun whenCloakedAmpLinkDetectedAndIsForMainFrameThenHandleCloakedAmpLink() = runTest {
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(SpecialUrlDetector.UrlType.CloakedAmpLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).handleCloakedAmpLink(EXAMPLE_URL)
    }

    @Test
    fun whenCloakedAmpLinkDetectedAndIsNotForMainFrameThenReturnFalse() = runTest {
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(SpecialUrlDetector.UrlType.CloakedAmpLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(false)
        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener, never()).handleCloakedAmpLink(any())
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameThenReturnTrueAndLoadCleanedUrl() = runTest {
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        val mockWebView = getImmediatelyInvokedMockWebView()
        assertTrue(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView).loadUrl(EXAMPLE_URL)
        verify(listener).startProcessingTrackingLink()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsOpenedInNewTabThenReturnTrueAndLoadCleanedUrl() = runTest {
        whenever(listener.linkOpenedInNewTab()).thenReturn(true)
        whenTrackingParametersDetectedAndIsForMainFrameThenReturnTrueAndLoadCleanedUrl()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsNotForMainFrameThenReturnFalse() = runTest {
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(false)
        val mockWebView = mock<WebView>()
        assertFalse(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView, never()).loadUrl(EXAMPLE_URL)
        verify(listener, never()).startProcessingTrackingLink()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndAppLinkIsHandledThenReturnTrueAndLoadCleanedUrl() = runTest {
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(EXAMPLE_URL))
        val appLink = SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL)
        whenever(specialUrlDetector.processUrl(anyString())).thenReturn(appLink)
        whenever(listener.handleAppLink(any(), any())).thenReturn(true)
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        val mockWebView = getImmediatelyInvokedMockWebView()
        assertTrue(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView).loadUrl(EXAMPLE_URL)
        verify(listener).startProcessingTrackingLink()
        verify(listener).handleAppLink(appLink, true)
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndAppLinkIsHandledAndIsOpenedInNewTabThenReturnTrueAndLoadCleanedUrl() = runTest {
        whenever(listener.linkOpenedInNewTab()).thenReturn(true)
        whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndAppLinkIsHandledThenReturnTrueAndLoadCleanedUrl()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndAppLinkIsNotHandledThenReturnFalseAndLoadCleanedUrl() = runTest {
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(EXAMPLE_URL))
        val appLink = SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL)
        whenever(specialUrlDetector.processUrl(anyString())).thenReturn(appLink)
        whenever(listener.handleAppLink(any(), any())).thenReturn(false)
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        val mockWebView = getImmediatelyInvokedMockWebView()
        assertFalse(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView).loadUrl(EXAMPLE_URL)
        verify(listener).startProcessingTrackingLink()
        verify(listener).handleAppLink(appLink, true)
    }

    @Test
    fun whenTrackingParamsDetectedAndIsForMainFrameAndIsAppLinkAndAppLinkIsNotHandledAndIsOpenedInNewTabThenReturnFalseAndLoadCleanedUrl() = runTest {
        whenever(listener.linkOpenedInNewTab()).thenReturn(true)
        whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndAppLinkIsNotHandledThenReturnFalseAndLoadCleanedUrl()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndListenerIsNullThenReturnFalse() = runTest {
        whenever(specialUrlDetector.determineType(any<Uri>())).thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(EXAMPLE_URL))
        val appLink = SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL)
        whenever(specialUrlDetector.processUrl(anyString())).thenReturn(appLink)
        testee.webViewClientListener = null
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        val mockWebView = mock<WebView>()
        assertFalse(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
    }

    private fun getImmediatelyInvokedMockWebView(): WebView {
        val mockWebView = mock<WebView>()
        whenever(mockWebView.post(any())).thenAnswer { invocation ->
            invocation.getArgument(0, Runnable::class.java).run()
            null
        }
        return mockWebView
    }

    private class TestWebView(context: Context) : WebView(context)

    companion object {
        const val EXAMPLE_URL = "example.com"
    }
}
