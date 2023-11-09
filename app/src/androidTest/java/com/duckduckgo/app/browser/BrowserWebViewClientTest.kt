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
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
import android.webkit.WebViewClient.ERROR_HOST_LOOKUP
import android.webkit.WebViewClient.ERROR_UNKNOWN
import androidx.core.net.toUri
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.anrs.api.CrashLogger.Crash
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.accessibility.AccessibilityManager
import com.duckduckgo.app.browser.WebViewErrorResponse.BAD_URL
import com.duckduckgo.app.browser.WebViewErrorResponse.CONNECTION
import com.duckduckgo.app.browser.WebViewErrorResponse.SSL_PROTOCOL_ERROR
import com.duckduckgo.app.browser.certificates.rootstore.TrustedCertificateStore
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.logindetection.DOMLoginDetector
import com.duckduckgo.app.browser.logindetection.WebNavigationEvent
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.print.PrintInjector
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autofill.api.BrowserAutofill
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.privacy.config.api.AmpLinks
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

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
    private val dosDetector: DosDetector = DosDetector()
    private val accessibilitySettings: AccessibilityManager = mock()
    private val trustedCertificateStore: TrustedCertificateStore = mock()
    private val webViewHttpAuthStore: WebViewHttpAuthStore = mock()
    private val thirdPartyCookieManager: ThirdPartyCookieManager = mock()
    private val browserAutofillConfigurator: BrowserAutofill.Configurator = mock()
    private val webResourceRequest: WebResourceRequest = mock()
    private val webResourceError: WebResourceError = mock()
    private val ampLinks: AmpLinks = mock()
    private val printInjector: PrintInjector = mock()
    private val internalTestUserChecker: InternalTestUserChecker = mock()
    private val adClickManager: AdClickManager = mock()
    private val autoconsent: Autoconsent = mock()
    private val pixel: Pixel = mock()
    private val crashLogger: CrashLogger = mock()
    private val jsPlugins = FakePluginPoint()

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
            cookieManagerProvider,
            loginDetector,
            dosDetector,
            thirdPartyCookieManager,
            TestScope(),
            coroutinesTestRule.testDispatcherProvider,
            browserAutofillConfigurator,
            accessibilitySettings,
            ampLinks,
            printInjector,
            internalTestUserChecker,
            adClickManager,
            autoconsent,
            pixel,
            crashLogger,
            jsPlugins,
        )
        testee.webViewClientListener = listener
        whenever(webResourceRequest.url).thenReturn(Uri.EMPTY)
        whenever(cookieManagerProvider.get()).thenReturn(cookieManager)
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenInterceptorCallOnPageStarted() {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(requestInterceptor).onPageStarted(EXAMPLE_URL)
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
    fun whenOnPageStartedCalledThenInjectJsCode() = runTest {
        assertEquals(0, jsPlugins.plugin.countStarted)
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        assertEquals(1, jsPlugins.plugin.countStarted)
        assertEquals(0, jsPlugins.plugin.countFinished)
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenProcessUriForThirdPartyCookiesCalled() = runTest {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(thirdPartyCookieManager).processUriForThirdPartyCookies(webView, EXAMPLE_URL.toUri())
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenInjectAutoconsentCalled() = runTest {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(autoconsent).injectAutoconsent(webView, EXAMPLE_URL)
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledThenListenerInstructedToUpdateNavigationState() {
        testee.onPageFinished(webView, EXAMPLE_URL)
        verify(listener).navigationStateChanged(any())
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledThenInjectJsCode() = runTest {
        assertEquals(0, jsPlugins.plugin.countFinished)
        testee.onPageFinished(webView, EXAMPLE_URL)
        assertEquals(1, jsPlugins.plugin.countFinished)
        assertEquals(0, jsPlugins.plugin.countStarted)
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
        verify(pixel).fire(WebViewPixelName.WEB_RENDERER_GONE_CRASH)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun whenRenderProcessGoneDueToNonCrashThenOtherDataStoreEntryIsIncremented() {
        val detail: RenderProcessGoneDetail = mock()
        whenever(detail.didCrash()).thenReturn(false)
        testee.onRenderProcessGone(webView, detail)
        verify(pixel).fire(WebViewPixelName.WEB_RENDERER_GONE_KILLED)
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
    fun whenOnPageFinishedThenNotifyAccessibilityManager() {
        testee.onPageFinished(webView, "http://example.com")

        verify(accessibilitySettings).onPageFinished(webView, "http://example.com")
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenInjectEmailAutofillJsCalled() {
        testee.onPageStarted(webView, null, null)
        verify(browserAutofillConfigurator).configureAutofillForCurrentPage(webView, null)
    }

    @Test
    fun whenShouldOverrideThrowsExceptionThenRecordException() = runTest {
        val exception = RuntimeException()
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenThrow(exception)
        testee.shouldOverrideUrlLoading(webView, "")
        verify(crashLogger).logCrash(Crash(shortName = "m_webview_should_override", t = exception))
    }

    @Test
    fun whenAppLinkDetectedAndIsHandledThenReturnTrue() = runTest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL)
            whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
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
            whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
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
            whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
                .thenReturn(SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL))
            testee.webViewClientListener = null
            assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
            verify(listener, never()).handleAppLink(any(), any())
        }
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndIsHandledThenReturnTrue() = runTest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
            whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
            whenever(webResourceRequest.isRedirect).thenReturn(false)
            whenever(listener.handleNonHttpAppLink(any())).thenReturn(true)
            whenever(webResourceRequest.isForMainFrame).thenReturn(true)
            assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
            verify(listener).handleNonHttpAppLink(urlType)
        }
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndIsNotForMainframeThenOnlyReturnTrue() = runTest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
            whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
            whenever(webResourceRequest.isRedirect).thenReturn(false)
            whenever(listener.handleNonHttpAppLink(any())).thenReturn(true)
            whenever(webResourceRequest.isForMainFrame).thenReturn(false)
            assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
            verifyNoInteractions(listener)
        }
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndIsHandledOnApiLessThan24ThenReturnTrue() = runTest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
            whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
            whenever(listener.handleNonHttpAppLink(any())).thenReturn(true)
            whenever(webResourceRequest.isForMainFrame).thenReturn(true)
            assertTrue(testee.shouldOverrideUrlLoading(webView, EXAMPLE_URL))
            verify(listener).handleNonHttpAppLink(urlType)
        }
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndIsNotHandledThenReturnFalse() = runTest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
            whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
            whenever(webResourceRequest.isRedirect).thenReturn(false)
            whenever(listener.handleNonHttpAppLink(any())).thenReturn(false)
            whenever(webResourceRequest.isForMainFrame).thenReturn(true)
            assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
            verify(listener).handleNonHttpAppLink(urlType)
        }
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndIsNotHandledOnApiLessThan24ThenReturnFalse() = runTest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
            whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
            whenever(listener.handleNonHttpAppLink(any())).thenReturn(false)
            assertFalse(testee.shouldOverrideUrlLoading(webView, EXAMPLE_URL))
            verify(listener).handleNonHttpAppLink(urlType)
        }
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndListenerIsNullThenReturnTrue() = runTest {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(
                SpecialUrlDetector.UrlType.NonHttpAppLink(
                    EXAMPLE_URL,
                    Intent(),
                    EXAMPLE_URL,
                ),
            )
            testee.webViewClientListener = null
            assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
            verify(listener, never()).handleNonHttpAppLink(any())
        }
    }

    @Test
    fun whenNonHttpAppLinkDetectedAndListenerIsNullOnApiLessThan24ThenReturnTrue() = runTest {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(
                SpecialUrlDetector.UrlType.NonHttpAppLink(
                    EXAMPLE_URL,
                    Intent(),
                    EXAMPLE_URL,
                ),
            )
            testee.webViewClientListener = null
            assertTrue(testee.shouldOverrideUrlLoading(webView, EXAMPLE_URL))
            verify(listener, never()).handleNonHttpAppLink(any())
        }
    }

    @Test
    fun whenAmpLinkDetectedAndIsForMainFrameThenReturnTrueAndLoadExtractedUrl() = runTest {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.ExtractedAmpLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        val mockWebView = getImmediatelyInvokedMockWebView()
        assertTrue(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView).loadUrl(EXAMPLE_URL)
        verify(listener).startProcessingTrackingLink()
    }

    @Test
    fun whenAmpLinkDetectedAndIsNotForMainFrameThenReturnFalse() = runTest {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.ExtractedAmpLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(false)
        val mockWebView = mock<WebView>()
        assertFalse(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView, never()).loadUrl(EXAMPLE_URL)
        verify(listener, never()).startProcessingTrackingLink()
    }

    @Test
    fun whenAmpLinkDetectedAndIsForMainFrameAndIsOpenedInNewTabThenReturnTrueAndLoadExtractedUrl() = runTest {
        whenever(listener.linkOpenedInNewTab()).thenReturn(true)
        whenAmpLinkDetectedAndIsForMainFrameThenReturnTrueAndLoadExtractedUrl()
    }

    @Test
    fun whenCloakedAmpLinkDetectedAndIsForMainFrameThenHandleCloakedAmpLink() = runTest {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.CloakedAmpLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).handleCloakedAmpLink(EXAMPLE_URL)
    }

    @Test
    fun whenCloakedAmpLinkDetectedAndIsNotForMainFrameThenReturnFalse() = runTest {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.CloakedAmpLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(false)
        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener, never()).handleCloakedAmpLink(any())
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameThenReturnTrueAndLoadCleanedUrl() = runTest {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(EXAMPLE_URL))
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
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(false)
        val mockWebView = mock<WebView>()
        assertFalse(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView, never()).loadUrl(EXAMPLE_URL)
        verify(listener, never()).startProcessingTrackingLink()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndAppLinkIsHandledThenReturnTrueAndLoadCleanedUrl() = runTest {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(EXAMPLE_URL))
        val appLink = SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL)
        whenever(specialUrlDetector.processUrl(anyString(), anyString())).thenReturn(appLink)
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
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(EXAMPLE_URL))
        val appLink = SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL)
        whenever(specialUrlDetector.processUrl(anyString(), anyString())).thenReturn(appLink)
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
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsExtractedAmpLinkThenReturnTrueAndLoadExtractedUrl() = runTest {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(EXAMPLE_URL))
        val ampLink = SpecialUrlDetector.UrlType.ExtractedAmpLink(extractedUrl = EXAMPLE_URL)
        whenever(specialUrlDetector.processUrl(anyString(), anyString())).thenReturn(ampLink)
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        val mockWebView = getImmediatelyInvokedMockWebView()
        assertTrue(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView).loadUrl(EXAMPLE_URL)
        verify(listener).startProcessingTrackingLink()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsExtractedAmpLinkAndIsOpenedInNewTabThenReturnTrueAndLoadExtractedUrl() = runTest {
        whenever(listener.linkOpenedInNewTab()).thenReturn(true)
        whenTrackingParametersDetectedAndIsForMainFrameAndIsExtractedAmpLinkThenReturnTrueAndLoadExtractedUrl()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndListenerIsNullThenReturnFalse() = runTest {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(EXAMPLE_URL))
        val appLink = SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL)
        whenever(specialUrlDetector.processUrl(anyString(), anyString())).thenReturn(appLink)
        testee.webViewClientListener = null
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        val mockWebView = mock<WebView>()
        assertFalse(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
    }

    @Test
    fun whenOnPageFinishedThenCallVerifyVerificationCompleted() {
        // run on the webview thread
        webView.post {
            testee.onPageFinished(webView, EXAMPLE_URL)
            verify(internalTestUserChecker).verifyVerificationCompleted(EXAMPLE_URL)
        }
    }

    @Test
    fun whenOnReceivedHttpErrorThenCallVerifyVerificationErrorReceived() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.url).thenReturn(EXAMPLE_URL)
        testee.onReceivedHttpError(mockWebView, null, null)

        verify(internalTestUserChecker).verifyVerificationErrorReceived(EXAMPLE_URL)
    }

    private fun getImmediatelyInvokedMockWebView(): WebView {
        val mockWebView = mock<WebView>()
        whenever(mockWebView.originalUrl).thenReturn(EXAMPLE_URL)
        whenever(mockWebView.post(any())).thenAnswer { invocation ->
            invocation.getArgument(0, Runnable::class.java).run()
            null
        }
        return mockWebView
    }

    @Test
    fun whenOnReceivedErrorAndErrorResponseIsBadUrlAndIsForMainFrameThenShowBadUrlError() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(webResourceError.errorCode).thenReturn(ERROR_HOST_LOOKUP)
        whenever(webResourceError.description).thenReturn("net::ERR_NAME_NOT_RESOLVED")
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)

        testee.onReceivedError(mockWebView, webResourceRequest, webResourceError)

        verify(testee.webViewClientListener)!!.onReceivedError(BAD_URL, "")
    }

    @Test
    fun whenOnReceivedErrorAndErrorResponseIsConnectionAndIsForMainFrameThenShowConnectionError() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(webResourceError.errorCode).thenReturn(ERROR_HOST_LOOKUP)
        whenever(webResourceError.description).thenReturn("net::ERR_INTERNET_DISCONNECTED")
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)

        testee.onReceivedError(mockWebView, webResourceRequest, webResourceError)

        verify(testee.webViewClientListener)!!.onReceivedError(CONNECTION, "")
    }

    @Test
    fun whenOnReceivedErrorAndErrorDescriptionDoesNotMatchThenDoNotShowBrowserError() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(webResourceError.errorCode).thenReturn(ERROR_HOST_LOOKUP)
        whenever(webResourceError.description).thenReturn("not matching")
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)

        testee.onReceivedError(mockWebView, webResourceRequest, webResourceError)

        verify(testee.webViewClientListener, times(0))!!.onReceivedError(any(), anyString())
    }

    @Test
    fun whenOnReceivedErrorAndErrorCodeIsNotHostLookupThenDoNotShowBrowserError() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(webResourceError.errorCode).thenReturn(ERROR_UNKNOWN)
        whenever(webResourceError.description).thenReturn("some error")
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)

        testee.onReceivedError(mockWebView, webResourceRequest, webResourceError)

        verify(testee.webViewClientListener, times(0))!!.onReceivedError(any(), anyString())
    }

    @Test
    fun whenOnReceivedErrorAndIsNotForMainFrameThenDoNotShowBrowserError() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(webResourceError.errorCode).thenReturn(ERROR_HOST_LOOKUP)
        whenever(webResourceError.description).thenReturn("net::ERR_NAME_NOT_RESOLVED")
        whenever(webResourceRequest.isForMainFrame).thenReturn(false)

        testee.onReceivedError(mockWebView, webResourceRequest, webResourceError)

        verify(testee.webViewClientListener, times(0))!!.onReceivedError(any(), anyString())
    }

    @Test
    fun whenOnReceivedErrorAndErrorCodeIsFailedSslHandshakeErrorAndIsForMainFrameThenShowBrowserError() {
        val requestUrl = "https://192.168.0.1"
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(webResourceError.errorCode).thenReturn(ERROR_FAILED_SSL_HANDSHAKE)
        whenever(webResourceError.description).thenReturn("net::ERR_SSL_PROTOCOL_ERROR")
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        whenever(webResourceRequest.url).thenReturn(requestUrl.toUri())

        testee.onReceivedError(mockWebView, webResourceRequest, webResourceError)

        verify(testee.webViewClientListener)!!.onReceivedError(SSL_PROTOCOL_ERROR, requestUrl)
    }

    private class TestWebView(context: Context) : WebView(context) {
        override fun getOriginalUrl(): String {
            return EXAMPLE_URL
        }
    }

    private class FakePluginPoint : PluginPoint<JsInjectorPlugin> {
        val plugin = FakeJsInjectorPlugin()
        override fun getPlugins(): Collection<JsInjectorPlugin> {
            return listOf(plugin)
        }
    }

    private class FakeJsInjectorPlugin : JsInjectorPlugin {
        var countFinished = 0
        var countStarted = 0

        override fun onPageStarted(
            webView: WebView,
            url: String?,
        ) {
            countStarted++
        }

        override fun onPageFinished(webView: WebView, url: String?) {
            countFinished++
        }
    }

    companion object {
        const val EXAMPLE_URL = "example.com"
    }
}
