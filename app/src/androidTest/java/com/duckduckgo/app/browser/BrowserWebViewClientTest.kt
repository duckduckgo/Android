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
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebBackForwardList
import android.webkit.WebHistoryItem
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
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.Web
import com.duckduckgo.app.browser.WebViewErrorResponse.BAD_URL
import com.duckduckgo.app.browser.WebViewErrorResponse.CONNECTION
import com.duckduckgo.app.browser.WebViewErrorResponse.SSL_PROTOCOL_ERROR
import com.duckduckgo.app.browser.certificates.rootstore.TrustedCertificateStore
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.logindetection.DOMLoginDetector
import com.duckduckgo.app.browser.logindetection.WebNavigationEvent
import com.duckduckgo.app.browser.mediaplayback.MediaPlayback
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.navigation.safeCopyBackForwardList
import com.duckduckgo.app.browser.pageloadpixel.PageLoadedHandler
import com.duckduckgo.app.browser.pageloadpixel.firstpaint.PagePaintedHandler
import com.duckduckgo.app.browser.print.PrintInjector
import com.duckduckgo.app.browser.trafficquality.AndroidFeaturesHeaderPlugin
import com.duckduckgo.app.browser.trafficquality.CustomHeaderAllowedChecker
import com.duckduckgo.app.browser.trafficquality.remote.AndroidFeaturesHeaderProvider
import com.duckduckgo.app.browser.uriloaded.UriLoadedManager
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autofill.api.BrowserAutofill
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerOrigin.SERP_AUTO
import com.duckduckgo.duckplayer.api.DuckPlayer.OpenDuckPlayerInNewTab
import com.duckduckgo.duckplayer.api.DuckPlayer.OpenDuckPlayerInNewTab.Off
import com.duckduckgo.duckplayer.api.DuckPlayer.OpenDuckPlayerInNewTab.On
import com.duckduckgo.duckplayer.api.DuckPlayer.OpenDuckPlayerInNewTab.Unavailable
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptPlugin
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.user.agent.api.ClientBrandHintProvider
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

private val mockToggle: Toggle = mock()

class BrowserWebViewClientTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: BrowserWebViewClient
    private lateinit var webView: TestWebView

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val requestRewriter: RequestRewriter = mock()
    private val specialUrlDetector: SpecialUrlDetector = mock()
    private val requestInterceptor: RequestInterceptor = mock()
    private val listener: WebViewClientListener = mock()
    private val cookieManagerProvider: CookieManagerProvider = mock()
    private val cookieManager: CookieManager = mock()
    private val loginDetector: DOMLoginDetector = mock()
    private val dosDetector: DosDetector = DosDetector()
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
    private val webViewVersionProvider: WebViewVersionProvider = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()
    private val deviceInfo: DeviceInfo = mock()
    private val pageLoadedHandler: PageLoadedHandler = mock()
    private val pagePaintedHandler: PagePaintedHandler = mock()
    private val mediaPlayback: MediaPlayback = mock()
    private val subscriptions: Subscriptions = mock()
    private val mockDuckPlayer: DuckPlayer = mock()
    private val navigationHistory: NavigationHistory = mock()
    private val mockDuckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()
    private val mockCustomHeaderGracePeriodChecker: CustomHeaderAllowedChecker = mock()
    private val mockFeaturesHeaderProvider: AndroidFeaturesHeaderProvider = mock()
    private val openInNewTabFlow: MutableSharedFlow<OpenDuckPlayerInNewTab> = MutableSharedFlow()
    private val mockUriLoadedManager: UriLoadedManager = mock()
    private val mockAndroidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()
    private val mockContentScopeExperiments: ContentScopeExperiments = mock()
    private val fakeAddDocumentStartJavaScriptPlugins = FakeAddDocumentStartJavaScriptPluginPoint()
    private val mockAndroidFeaturesHeaderPlugin = AndroidFeaturesHeaderPlugin(
        mockDuckDuckGoUrlDetector,
        mockCustomHeaderGracePeriodChecker,
        mockAndroidBrowserConfigFeature,
        mockFeaturesHeaderProvider,
        mock(),
    )
    private val mockDuckChat: DuckChat = mock()

    @UiThreadTest
    @Before
    fun setup() = runTest {
        webView = TestWebView(context)
        whenever(mockDuckPlayer.observeShouldOpenInNewTab()).thenReturn(openInNewTabFlow)
        whenever(mockContentScopeExperiments.getActiveExperiments()).thenReturn(listOf(mockToggle))
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
            coroutinesTestRule.testScope,
            coroutinesTestRule.testDispatcherProvider,
            browserAutofillConfigurator,
            ampLinks,
            printInjector,
            internalTestUserChecker,
            adClickManager,
            autoconsent,
            pixel,
            crashLogger,
            jsPlugins,
            currentTimeProvider,
            pageLoadedHandler,
            pagePaintedHandler,
            navigationHistory,
            mediaPlayback,
            subscriptions,
            mockDuckPlayer,
            mockDuckDuckGoUrlDetector,
            mockUriLoadedManager,
            mockAndroidFeaturesHeaderPlugin,
            mockDuckChat,
            mockContentScopeExperiments,
            fakeAddDocumentStartJavaScriptPlugins,
        )
        testee.webViewClientListener = listener
        whenever(webResourceRequest.url).thenReturn(Uri.EMPTY)
        whenever(cookieManagerProvider.get()).thenReturn(cookieManager)
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(0)
        whenever(webViewVersionProvider.getMajorVersion()).thenReturn("1")
        whenever(deviceInfo.appVersion).thenReturn("1")
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenInterceptorCallOnPageStarted() {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(requestInterceptor).onPageStarted(EXAMPLE_URL)
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenListenerNotified() = runTest {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(listener).pageStarted(any(), eq(listOf(mockToggle)))
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
    fun whenOnPageCommitVisibleCalledThenListenerIsNotified() {
        val mockWebView: WebView = mock()
        whenever(mockWebView.url).thenReturn(EXAMPLE_URL)
        whenever(mockWebView.safeCopyBackForwardList()).thenReturn(TestBackForwardList())
        testee.onPageCommitVisible(mockWebView, EXAMPLE_URL)
        verify(listener).onPageCommitVisible(any(), any())
    }

    @UiThreadTest
    @Test
    fun whenOnPageCommitVisibleCalledWithDifferentUrlToPreviousThenListenerNotNotified() {
        val mockWebView: WebView = mock()
        whenever(mockWebView.url).thenReturn(EXAMPLE_URL)
        whenever(mockWebView.safeCopyBackForwardList()).thenReturn(TestBackForwardList())
        testee.onPageCommitVisible(mockWebView, EXAMPLE_URL)
        testee.onPageCommitVisible(mockWebView, "foo.com")
        verify(listener).onPageCommitVisible(any(), any())
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenEventSentToLoginDetector() {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(loginDetector).onEvent(WebNavigationEvent.OnPageStarted(webView))
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenInjectJsCode() {
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
    fun whenOnPageStartedCalledThenInjectAutoconsentCalled() {
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(autoconsent).injectAutoconsent(webView, EXAMPLE_URL)
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledForUrlWithUserGestureNotRequiredForUrlThenMediaPlaybackRequiresUserGestureIsFalse() {
        whenever(mediaPlayback.doesMediaPlaybackRequireUserGestureForUrl(EXAMPLE_URL)).thenReturn(false)
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        assertFalse(webView.settings.mediaPlaybackRequiresUserGesture)
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledForUrlWithUserGestureRequiredForUrlThenMediaPlaybackRequiresUserGestureIsTrue() {
        whenever(mediaPlayback.doesMediaPlaybackRequireUserGestureForUrl(EXAMPLE_URL)).thenReturn(true)
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        assertTrue(webView.settings.mediaPlaybackRequiresUserGesture)
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledThenListenerNotified() {
        testee.onPageFinished(webView, EXAMPLE_URL)
        verify(listener).pageFinished(any(), eq(EXAMPLE_URL))
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledThenInjectJsCode() {
        assertEquals(0, jsPlugins.plugin.countFinished)
        testee.onPageFinished(webView, EXAMPLE_URL)
        assertEquals(1, jsPlugins.plugin.countFinished)
        assertEquals(0, jsPlugins.plugin.countStarted)
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedThenReturnActiveExperiments() {
        val captor = argumentCaptor<List<Toggle>>()
        testee.onPageStarted(webView, EXAMPLE_URL, null)
        verify(listener).pageStarted(any(), captor.capture())
        assertTrue(captor.firstValue.contains(mockToggle))
    }

    @UiThreadTest
    @Test
    fun whenTriggerJsInitThenInjectJsCode() {
        assertEquals(0, fakeAddDocumentStartJavaScriptPlugins.plugin.countInitted)
        testee.configureWebView(DuckDuckGoWebView(context))
        assertEquals(1, fakeAddDocumentStartJavaScriptPlugins.plugin.countInitted)
    }

    @UiThreadTest
    @Test
    fun whenOnReceivedHttpAuthRequestThenListenerNotified() {
        val mockHandler = mock<HttpAuthHandler>()
        val authenticationRequest = BasicAuthenticationRequest(mockHandler, "example.com", EXAMPLE_URL, EXAMPLE_URL)
        webView.webViewUrl = EXAMPLE_URL
        testee.onReceivedHttpAuthRequest(webView, mockHandler, "example.com", EXAMPLE_URL)
        verify(listener).requiresAuthentication(authenticationRequest)
    }

    @UiThreadTest
    @Test
    fun whenShouldInterceptRequestThenEventSentToLoginDetector() {
        val webResourceRequest = mock<WebResourceRequest>()
        testee.shouldInterceptRequest(webView, webResourceRequest)
        verify(loginDetector).onEvent(WebNavigationEvent.ShouldInterceptRequest(webView, webResourceRequest))
    }

    @UiThreadTest
    @Test
    fun whenShouldInterceptRequestThenShouldInterceptWithUri() {
        TestScope().launch {
            val webResourceRequest = mock<WebResourceRequest>()
            testee.shouldInterceptRequest(webView, webResourceRequest)
            verify(requestInterceptor).shouldIntercept(any(), any(), any<Uri>(), any())
        }
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
    fun whenOnPageStartedCalledThenInjectEmailAutofillJsCalled() {
        testee.onPageStarted(webView, null, null)
        verify(browserAutofillConfigurator).configureAutofillForCurrentPage(webView, null)
    }

    @UiThreadTest
    @Test
    fun whenShouldOverrideThrowsExceptionThenRecordException() {
        val exception = RuntimeException()
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenThrow(exception)
        testee.shouldOverrideUrlLoading(webView, webResourceRequest)
        verify(crashLogger).logCrash(Crash(shortName = "m_webview_should_override", t = exception))
    }

    @UiThreadTest
    @Test
    fun whenPrivacyProLinkDetectedThenLaunchPrivacyProAndReturnTrue() {
        val urlType = SpecialUrlDetector.UrlType.ShouldLaunchPrivacyProLink
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(subscriptions).launchPrivacyPro(any(), any())
    }

    @UiThreadTest
    @Test
    fun whenDuckChatLinkDetectedThenLaunchDuckChatAndReturnTrue() {
        val urlType = SpecialUrlDetector.UrlType.ShouldLaunchDuckChatLink
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.url).thenReturn("https://duckduckgo.com/?q=example&ia=chat&duckai=5".toUri())
        assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))

        verify(mockDuckChat).openDuckChatWithPrefill("example")
    }

    @UiThreadTest
    @Test
    fun whenDuckChatLinkDetectedWithoutQueryThenLaunchDuckChatWithoutQueryAndReturnTrue() {
        val urlType = SpecialUrlDetector.UrlType.ShouldLaunchDuckChatLink
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.url).thenReturn("https://duckduckgo.com/?ia=chat".toUri())
        assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(mockDuckChat).openDuckChat()
    }

    @UiThreadTest
    @Test
    fun whenDuckChatLinkDetectedAndExceptionThrownThenDoNotLaunchDuckChatAndReturnFalse() {
        val urlType = SpecialUrlDetector.UrlType.ShouldLaunchDuckChatLink
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        val mockUri: Uri = mock()
        whenever(mockUri.getQueryParameter(anyString())).thenThrow(RuntimeException())
        whenever(webResourceRequest.url).thenReturn(mockUri)
        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verifyNoInteractions(mockDuckChat)
    }

    @UiThreadTest
    @Test
    fun whenShouldOverrideWithShouldNavigateToDuckPlayerSetOriginToSerpAuto() = runTest {
        val urlType = SpecialUrlDetector.UrlType.ShouldLaunchDuckPlayerLink("duck://player/1234".toUri())
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        whenever(webResourceRequest.isRedirect).thenReturn(false)
        whenever(webResourceRequest.url).thenReturn("www.youtube.com/watch?v=1234".toUri())
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoUrl(any())).thenReturn(true)
        val mockClientProvider: ClientBrandHintProvider = mock()
        whenever(mockClientProvider.shouldChangeBranding(any())).thenReturn(false)
        testee.clientProvider = mockClientProvider
        doNothing().whenever(listener).willOverrideUrl(any())
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.url).thenReturn("www.duckduckgo.com")
        openInNewTabFlow.emit(Off)

        assertFalse(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockDuckPlayer).setDuckPlayerOrigin(SERP_AUTO)
    }

    @UiThreadTest
    @Test
    fun whenShouldOverrideWithShouldNavigateToDuckPlayerButNotMainFrameDoNothing() = runTest {
        val urlType = SpecialUrlDetector.UrlType.ShouldLaunchDuckPlayerLink("duck://player/1234".toUri())
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.isForMainFrame).thenReturn(false)
        whenever(webResourceRequest.isRedirect).thenReturn(false)
        whenever(webResourceRequest.url).thenReturn("www.youtube.com/watch?v=1234".toUri())
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoUrl(any())).thenReturn(true)
        val mockClientProvider: ClientBrandHintProvider = mock()
        whenever(mockClientProvider.shouldChangeBranding(any())).thenReturn(false)
        testee.clientProvider = mockClientProvider
        doNothing().whenever(listener).willOverrideUrl(any())
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.url).thenReturn("www.duckduckgo.com")
        openInNewTabFlow.emit(Off)

        assertFalse(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
    }

    @Test
    fun whenShouldOverrideWithWebThenDoNotAddQueryParam() = runTest {
        val urlType = Web("www.youtube.com/watch?v=1234")
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        whenever(webResourceRequest.isRedirect).thenReturn(false)
        whenever(webResourceRequest.url).thenReturn("www.youtube.com/watch?v=1234".toUri())
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoUrl(any())).thenReturn(true)
        val mockClientProvider: ClientBrandHintProvider = mock()
        whenever(mockClientProvider.shouldChangeBranding(any())).thenReturn(false)
        testee.clientProvider = mockClientProvider
        doNothing().whenever(listener).willOverrideUrl(any())
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.url).thenReturn("www.duckduckgo.com")
        openInNewTabFlow.emit(Off)

        assertFalse(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
    }

    @UiThreadTest
    @Test
    fun whenAppLinkDetectedAndIsHandledThenReturnTrue() {
        val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL)
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.isRedirect).thenReturn(false)
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        whenever(listener.handleAppLink(any(), any())).thenReturn(true)
        assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).handleAppLink(urlType, isForMainFrame = true)
    }

    @UiThreadTest
    @Test
    fun whenAppLinkDetectedAndIsNotHandledThenReturnFalse() {
        val urlType = SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL)
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.isRedirect).thenReturn(false)
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        whenever(listener.handleAppLink(any(), any())).thenReturn(false)
        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).handleAppLink(urlType, isForMainFrame = true)
    }

    @Test
    fun whenAppLinkDetectedAndListenerIsNullThenReturnFalse() {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.AppLink(uriString = EXAMPLE_URL))
        testee.webViewClientListener = null
        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener, never()).handleAppLink(any(), any())
    }

    @UiThreadTest
    @Test
    fun whenNonHttpAppLinkDetectedAndIsHandledThenReturnTrue() {
        val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.isRedirect).thenReturn(false)
        whenever(listener.handleNonHttpAppLink(any())).thenReturn(true)
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).handleNonHttpAppLink(urlType)
    }

    @UiThreadTest
    @Test
    fun whenShouldLaunchDuckPlayerThenOpenInNewTabAndReturnTrue() = runTest {
        openInNewTabFlow.emit(On)
        val urlType = SpecialUrlDetector.UrlType.ShouldLaunchDuckPlayerLink(EXAMPLE_URL.toUri())
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.url).thenReturn(EXAMPLE_URL.toUri())
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        whenever(mockDuckPlayer.shouldOpenDuckPlayerInNewTab()).thenReturn(On)
        testee.clientProvider = mock()

        assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).onShouldOverride()
        verify(listener).openLinkInNewTab(EXAMPLE_URL.toUri())
    }

    @UiThreadTest
    @Test
    fun whenShouldLaunchDuckPlayerInNewTabButSameUrlThenDoNothing() = runTest {
        openInNewTabFlow.emit(On)
        val urlType = SpecialUrlDetector.UrlType.ShouldLaunchDuckPlayerLink(EXAMPLE_URL.toUri())
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.url).thenReturn(EXAMPLE_URL.toUri())
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        whenever(mockDuckPlayer.shouldOpenDuckPlayerInNewTab()).thenReturn(On)
        testee.clientProvider = mock()
        (webView as TestWebView).webViewUrl = EXAMPLE_URL

        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener, never()).openLinkInNewTab(EXAMPLE_URL.toUri())
    }

    @UiThreadTest
    @Test
    fun whenShouldLaunchDuckPlayerButNotMainframeThenDoNothing() = runTest {
        openInNewTabFlow.emit(On)
        val urlType = SpecialUrlDetector.UrlType.ShouldLaunchDuckPlayerLink(EXAMPLE_URL.toUri())
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.url).thenReturn(EXAMPLE_URL.toUri())
        whenever(webResourceRequest.isForMainFrame).thenReturn(false)
        whenever(mockDuckPlayer.shouldOpenDuckPlayerInNewTab()).thenReturn(On)

        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).onShouldOverride()
        verify(listener, never()).openLinkInNewTab(EXAMPLE_URL.toUri())
    }

    @UiThreadTest
    @Test
    fun whenShouldLaunchDuckPlayerButNotOpenInNewTabThenReturnFalse() = runTest {
        openInNewTabFlow.emit(Off)
        val urlType = SpecialUrlDetector.UrlType.ShouldLaunchDuckPlayerLink(EXAMPLE_URL.toUri())
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.url).thenReturn(EXAMPLE_URL.toUri())

        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).onShouldOverride()
        verify(listener, never()).openLinkInNewTab(EXAMPLE_URL.toUri())
    }

    @UiThreadTest
    @Test
    fun whenShouldLaunchDuckPlayerButOpenInNewTabUnavailableThenReturnFalse() = runTest {
        openInNewTabFlow.emit(Unavailable)
        val urlType = SpecialUrlDetector.UrlType.ShouldLaunchDuckPlayerLink(EXAMPLE_URL.toUri())
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.url).thenReturn(EXAMPLE_URL.toUri())

        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).onShouldOverride()
        verify(listener, never()).openLinkInNewTab(EXAMPLE_URL.toUri())
    }

    @UiThreadTest
    @Test
    fun whenShouldOverrideWithShouldNavigateToDuckPlayerFromSerpAndOpenInNewTabThenSetOriginToSerpAuto() = runTest {
        val urlType = SpecialUrlDetector.UrlType.ShouldLaunchDuckPlayerLink("duck://player/1234".toUri())
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        whenever(webResourceRequest.isRedirect).thenReturn(false)
        whenever(webResourceRequest.url).thenReturn("www.youtube.com/watch?v=1234".toUri())
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoUrl(any())).thenReturn(true)
        val mockClientProvider: ClientBrandHintProvider = mock()
        whenever(mockClientProvider.shouldChangeBranding(any())).thenReturn(false)
        whenever(mockDuckPlayer.shouldOpenDuckPlayerInNewTab()).thenReturn(On)
        testee.clientProvider = mockClientProvider
        doNothing().whenever(listener).willOverrideUrl(any())
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.url).thenReturn("www.duckduckgo.com")
        openInNewTabFlow.emit(Off)

        assertFalse(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockDuckPlayer).setDuckPlayerOrigin(SERP_AUTO)
    }

    @UiThreadTest
    @Test
    fun whenNonHttpAppLinkDetectedAndIsNotForMainframeThenOnlyReturnTrue() {
        val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.isRedirect).thenReturn(false)
        whenever(listener.handleNonHttpAppLink(any())).thenReturn(true)
        whenever(webResourceRequest.isForMainFrame).thenReturn(false)
        assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
    }

    @UiThreadTest
    @Test
    fun whenNonHttpAppLinkDetectedAndIsNotHandledThenReturnFalse() {
        val urlType = SpecialUrlDetector.UrlType.NonHttpAppLink(EXAMPLE_URL, Intent(), EXAMPLE_URL)
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(webResourceRequest.isRedirect).thenReturn(false)
        whenever(listener.handleNonHttpAppLink(any())).thenReturn(false)
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).handleNonHttpAppLink(urlType)
    }

    @UiThreadTest
    @Test
    fun whenNonHttpAppLinkDetectedAndListenerIsNullThenReturnTrue() {
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

    @Test
    fun whenAmpLinkDetectedAndIsForMainFrameThenReturnTrueAndLoadExtractedUrl() {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.ExtractedAmpLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        val mockWebView = getImmediatelyInvokedMockWebView()
        assertTrue(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView).loadUrl(EXAMPLE_URL)
        verify(listener).startProcessingTrackingLink()
    }

    @Test
    fun whenAmpLinkDetectedAndIsNotForMainFrameThenReturnFalse() {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.ExtractedAmpLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(false)
        val mockWebView = mock<WebView>()
        assertFalse(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView, never()).loadUrl(EXAMPLE_URL)
        verify(listener, never()).startProcessingTrackingLink()
    }

    @Test
    fun whenAmpLinkDetectedAndIsForMainFrameAndIsOpenedInNewTabThenReturnTrueAndLoadExtractedUrl() {
        whenever(listener.linkOpenedInNewTab()).thenReturn(true)
        whenAmpLinkDetectedAndIsForMainFrameThenReturnTrueAndLoadExtractedUrl()
    }

    @UiThreadTest
    @Test
    fun whenCloakedAmpLinkDetectedAndIsForMainFrameThenHandleCloakedAmpLink() {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.CloakedAmpLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        assertTrue(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener).handleCloakedAmpLink(EXAMPLE_URL)
    }

    @Test
    fun whenCloakedAmpLinkDetectedAndIsNotForMainFrameThenReturnFalse() {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.CloakedAmpLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(false)
        assertFalse(testee.shouldOverrideUrlLoading(webView, webResourceRequest))
        verify(listener, never()).handleCloakedAmpLink(any())
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameThenReturnTrueAndLoadCleanedUrl() {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        val mockWebView = getImmediatelyInvokedMockWebView()
        assertTrue(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView).loadUrl(EXAMPLE_URL)
        verify(listener).startProcessingTrackingLink()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsOpenedInNewTabThenReturnTrueAndLoadCleanedUrl() {
        whenever(listener.linkOpenedInNewTab()).thenReturn(true)
        whenTrackingParametersDetectedAndIsForMainFrameThenReturnTrueAndLoadCleanedUrl()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsNotForMainFrameThenReturnFalse() {
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any()))
            .thenReturn(SpecialUrlDetector.UrlType.TrackingParameterLink(EXAMPLE_URL))
        whenever(webResourceRequest.isForMainFrame).thenReturn(false)
        val mockWebView = mock<WebView>()
        assertFalse(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))
        verify(mockWebView, never()).loadUrl(EXAMPLE_URL)
        verify(listener, never()).startProcessingTrackingLink()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndAppLinkIsHandledThenReturnTrueAndLoadCleanedUrl() {
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
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndAppLinkIsHandledAndIsOpenedInNewTabThenReturnTrueAndLoadCleanedUrl() {
        whenever(listener.linkOpenedInNewTab()).thenReturn(true)
        whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndAppLinkIsHandledThenReturnTrueAndLoadCleanedUrl()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndAppLinkIsNotHandledThenReturnFalseAndLoadCleanedUrl() {
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
    fun whenTrackingParamsDetectedAndIsForMainFrameAndIsAppLinkAndAppLinkIsNotHandledAndIsOpenedInNewTabThenReturnFalseAndLoadCleanedUrl() {
        whenever(listener.linkOpenedInNewTab()).thenReturn(true)
        whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndAppLinkIsNotHandledThenReturnFalseAndLoadCleanedUrl()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsExtractedAmpLinkThenReturnTrueAndLoadExtractedUrl() {
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
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsExtractedAmpLinkAndIsOpenedInNewTabThenReturnTrueAndLoadExtractedUrl() {
        whenever(listener.linkOpenedInNewTab()).thenReturn(true)
        whenTrackingParametersDetectedAndIsForMainFrameAndIsExtractedAmpLinkThenReturnTrueAndLoadExtractedUrl()
    }

    @Test
    fun whenTrackingParametersDetectedAndIsForMainFrameAndIsAppLinkAndListenerIsNullThenReturnFalse() {
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
        whenever(mockWebView.url).thenReturn(EXAMPLE_URL)
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

    @Test
    fun whenRewriteRequestWithCustomQueryParamsAndNotOpenedInNewTabThenLoadRewrittenUrl() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        val urlType = SpecialUrlDetector.UrlType.Web(EXAMPLE_URL)
        val rewrittenUrl = "https://rewritten-example.com"
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(requestRewriter.shouldRewriteRequest(any())).thenReturn(true)
        whenever(requestRewriter.rewriteRequestWithCustomQueryParams(any())).thenReturn(rewrittenUrl.toUri())
        whenever(listener.linkOpenedInNewTab()).thenReturn(false)

        assertTrue(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))

        verify(listener).linkOpenedInNewTab()
        verify(mockWebView, times(0)).post(any())
        verify(mockWebView).loadUrl(rewrittenUrl)
    }

    @Test
    fun whenRewriteRequestWithCustomQueryParamsAndOpenedInNewTabThenLoadRewrittenUrlInPost() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        val urlType = SpecialUrlDetector.UrlType.Web(EXAMPLE_URL)
        val rewrittenUrl = "https://rewritten-example.com"
        whenever(specialUrlDetector.determineType(initiatingUrl = any(), uri = any())).thenReturn(urlType)
        whenever(requestRewriter.shouldRewriteRequest(any())).thenReturn(true)
        whenever(requestRewriter.rewriteRequestWithCustomQueryParams(any())).thenReturn(rewrittenUrl.toUri())
        whenever(listener.linkOpenedInNewTab()).thenReturn(true)

        assertTrue(testee.shouldOverrideUrlLoading(mockWebView, webResourceRequest))

        verify(listener).linkOpenedInNewTab()
        verify(mockWebView).post(any())
        verify(mockWebView).loadUrl(rewrittenUrl)
    }

    @Test
    fun whenPageFinishesBeforeStartingThenPixelIsNotFired() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        testee.onPageFinished(mockWebView, EXAMPLE_URL)
        verify(pageLoadedHandler, never()).onPageLoaded(any(), any(), any(), any(), any())
    }

    @Test
    fun whenPageFinishesThenPixelIsAddedWithTotalPageLoadTime() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.progress).thenReturn(100)
        whenever(mockWebView.safeCopyBackForwardList()).thenReturn(TestBackForwardList())
        whenever(mockWebView.settings).thenReturn(mock())
        testee.onPageStarted(mockWebView, EXAMPLE_URL, null)
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(10)
        testee.onPageFinished(mockWebView, EXAMPLE_URL)
        val startArgumentCaptor = argumentCaptor<Long>()
        val endArgumentCaptor = argumentCaptor<Long>()
        verify(pageLoadedHandler).onPageLoaded(any(), eq(null), startArgumentCaptor.capture(), endArgumentCaptor.capture(), any())
        assertEquals(0L, startArgumentCaptor.firstValue)
        assertEquals(10L, endArgumentCaptor.firstValue)
    }

    @Test
    fun whenPageFinishesAfterStartingAndPageAboutBlankThenPixelIsNotAdded() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.progress).thenReturn(100)
        whenever(mockWebView.safeCopyBackForwardList()).thenReturn(TestBackForwardList())
        whenever(mockWebView.settings).thenReturn(mock())
        testee.onPageStarted(mockWebView, "about:blank", null)
        testee.onPageFinished(mockWebView, "about:blank")
        verify(pageLoadedHandler, never()).onPageLoaded(any(), any(), any(), any(), any())
    }

    @Test
    fun whenPageFinishesAfterStartingAndProgressIsNot100ThenPixelIsNotAdded() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.settings).thenReturn(mock())
        testee.onPageStarted(mockWebView, EXAMPLE_URL, null)
        testee.onPageFinished(mockWebView, EXAMPLE_URL)
        verify(pageLoadedHandler, never()).onPageLoaded(any(), any(), any(), any(), any())
    }

    @Test
    fun whenPageFinishesThenHistoryIsSubmitted() {
        runTest {
            val mockWebView = getImmediatelyInvokedMockWebView()
            whenever(mockWebView.progress).thenReturn(100)
            whenever(mockWebView.safeCopyBackForwardList()).thenReturn(TestBackForwardList())
            whenever(mockWebView.settings).thenReturn(mock())
            whenever(mockDuckPlayer.isSimulatedYoutubeNoCookie(any())).thenReturn(false)
            whenever(mockDuckPlayer.isYoutubeWatchUrl(any())).thenReturn(false)
            testee.onPageStarted(mockWebView, EXAMPLE_URL, null)
            whenever(currentTimeProvider.elapsedRealtime()).thenReturn(10)
            testee.onPageFinished(mockWebView, EXAMPLE_URL)
            verify(navigationHistory).saveToHistory(any(), eq(null))
        }
    }

    @Test
    fun whenPageStartedMoreThanOnceThenStartTimeIsNotUpdated() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.progress).thenReturn(100)
        whenever(mockWebView.safeCopyBackForwardList()).thenReturn(TestBackForwardList())
        whenever(mockWebView.settings).thenReturn(mock())
        testee.onPageStarted(mockWebView, EXAMPLE_URL, null)
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(5)
        testee.onPageStarted(mockWebView, EXAMPLE_URL, null)
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(10)
        testee.onPageFinished(mockWebView, EXAMPLE_URL)

        val startArgumentCaptor = argumentCaptor<Long>()
        val endArgumentCaptor = argumentCaptor<Long>()
        verify(pageLoadedHandler).onPageLoaded(any(), eq(null), startArgumentCaptor.capture(), endArgumentCaptor.capture(), any())
        assertEquals(0L, startArgumentCaptor.firstValue)
        assertEquals(10L, endArgumentCaptor.firstValue)
    }

    @Test
    fun whenPageLoadErrorThenDiscardStartTime() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.progress).thenReturn(100)
        whenever(mockWebView.safeCopyBackForwardList()).thenReturn(TestBackForwardList())
        whenever(mockWebView.settings).thenReturn(mock())
        testee.onPageStarted(mockWebView, EXAMPLE_URL, null)
        whenever(webResourceError.description).thenReturn("net::ERR_NAME_NOT_RESOLVED")
        whenever(webResourceError.errorCode).thenReturn(ERROR_HOST_LOOKUP)
        whenever(webResourceRequest.isForMainFrame).thenReturn(true)
        testee.onReceivedError(mockWebView, webResourceRequest, webResourceError)
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(5)
        testee.onPageStarted(mockWebView, EXAMPLE_URL, null)
        whenever(currentTimeProvider.elapsedRealtime()).thenReturn(10)
        testee.onPageFinished(mockWebView, EXAMPLE_URL)
        val startArgumentCaptor = argumentCaptor<Long>()
        val endArgumentCaptor = argumentCaptor<Long>()
        verify(pageLoadedHandler).onPageLoaded(any(), eq(null), startArgumentCaptor.capture(), endArgumentCaptor.capture(), any())
        assertEquals(5L, startArgumentCaptor.firstValue)
        assertEquals(10L, endArgumentCaptor.firstValue)
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledBeforeCompletionThenJsCodeNotInjected() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.progress).thenReturn(10)
        whenever(mockWebView.settings).thenReturn(mock())

        assertEquals(0, jsPlugins.plugin.countFinished)
        testee.onPageFinished(mockWebView, EXAMPLE_URL)
        assertEquals(0, jsPlugins.plugin.countFinished)
        assertEquals(0, jsPlugins.plugin.countStarted)
    }

    @Test
    fun whenOnPageFinishedCalledBeforeCompleteThenVerifyVerificationCompletedNotCalled() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.progress).thenReturn(10)
        whenever(mockWebView.settings).thenReturn(mock())
        whenever(mockWebView.safeCopyBackForwardList()).thenReturn(TestBackForwardList())

        testee.onPageFinished(mockWebView, EXAMPLE_URL)
        verifyNoInteractions(internalTestUserChecker)
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledBeforeCompleteThenOnPageFinishedNotInvoked() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.progress).thenReturn(10)
        whenever(mockWebView.settings).thenReturn(mock())
        whenever(mockWebView.safeCopyBackForwardList()).thenReturn(TestBackForwardList())

        testee.onPageFinished(mockWebView, EXAMPLE_URL)
        verifyNoInteractions(listener)
    }

    @Test
    fun whenOnPageFinishedCalledThenPrintInjectorInjected() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.progress).thenReturn(100)
        whenever(mockWebView.settings).thenReturn(mock())
        whenever(mockWebView.safeCopyBackForwardList()).thenReturn(TestBackForwardList())

        testee.onPageFinished(mockWebView, EXAMPLE_URL)
        verify(printInjector).injectPrint(mockWebView)
    }

    @Test
    fun whenOnPageFinishedBeforeCompleteThenPrintInjectorNotInjected() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.progress).thenReturn(10)
        whenever(mockWebView.settings).thenReturn(mock())
        whenever(mockWebView.safeCopyBackForwardList()).thenReturn(TestBackForwardList())

        testee.onPageFinished(mockWebView, EXAMPLE_URL)
        verifyNoInteractions(printInjector)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun whenSSLErrorReceivedForMainURLThenListenerCalled() {
        val mockWebView = getImmediatelyInvokedMockWebView()
        whenever(mockWebView.url).thenReturn(EXAMPLE_URL)

        val handler = aHandler()
        val sslError = SslError(SslError.SSL_EXPIRED, aRSASslCertificate(), EXAMPLE_URL)

        testee.onReceivedSslError(mockWebView, handler, sslError)

        verify(listener).onReceivedSslError(any(), any())
    }

    @UiThreadTest
    @Test
    fun whenPageLoadsThenFireUriLoadedPixel() {
        val mockWebView = getImmediatelyInvokedMockWebView()

        whenever(mockWebView.settings).thenReturn(mock())
        whenever(mockWebView.safeCopyBackForwardList()).thenReturn(TestBackForwardList())
        whenever(mockWebView.progress).thenReturn(100)

        testee.onPageStarted(mockWebView, EXAMPLE_URL, null)
        testee.onPageFinished(mockWebView, EXAMPLE_URL)

        mockUriLoadedManager.sendUriLoadedPixel()
    }

    private class TestWebView(context: Context) : WebView(context) {

        var webViewUrl: String? = null

        override fun getUrl(): String? {
            return webViewUrl
        }

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
            isDesktopMode: Boolean?,
            activeExperiments: List<Toggle>,
        ) {
            countStarted++
        }

        override fun onPageFinished(
            webView: WebView,
            url: String?,
            site: Site?,
        ) {
            countFinished++
        }
    }

    private class TestBackForwardList : WebBackForwardList() {

        private val fakeHistory: MutableList<WebHistoryItem> = mutableListOf()
        private var fakeCurrentIndex = -1

        fun addPageToHistory(webHistoryItem: WebHistoryItem) {
            fakeHistory.add(webHistoryItem)
            fakeCurrentIndex++
        }

        override fun getSize() = fakeHistory.size

        override fun getItemAtIndex(index: Int): WebHistoryItem = fakeHistory[index]

        override fun getCurrentItem(): WebHistoryItem? = null

        override fun getCurrentIndex(): Int = fakeCurrentIndex

        override fun clone(): WebBackForwardList = throw NotImplementedError()
    }

    private class TestHistoryItem(
        private val url: String,
    ) : WebHistoryItem() {

        override fun getUrl(): String = url

        override fun getOriginalUrl(): String = url

        override fun getTitle(): String = url

        override fun getFavicon(): Bitmap = throw NotImplementedError()

        override fun clone(): WebHistoryItem = throw NotImplementedError()
    }

    fun aHandler(): SslErrorHandler {
        val handler = mock<SslErrorHandler>().apply {
        }
        return handler
    }

    private fun aRSASslCertificate(): SslCertificate {
        val certificate = mock<X509Certificate>().apply {
            val key = mock<RSAPublicKey>().apply {
                whenever(this.algorithm).thenReturn("rsa")
                whenever(this.modulus).thenReturn(BigInteger("1"))
            }
            whenever(this.publicKey).thenReturn(key)
        }
        return mock<SslCertificate>().apply {
            whenever(x509Certificate).thenReturn(certificate)
        }
    }

    companion object {
        const val EXAMPLE_URL = "https://example.com"
    }

    class FakeAddDocumentStartJavaScriptPlugin : AddDocumentStartJavaScriptPlugin {

        var countInitted = 0
            private set

        override fun addDocumentStartJavaScript(
            activeExperiments: List<Toggle>,
            webView: WebView,
        ) {
            countInitted++
        }
    }

    class FakeAddDocumentStartJavaScriptPluginPoint : PluginPoint<AddDocumentStartJavaScriptPlugin> {

        val plugin = FakeAddDocumentStartJavaScriptPlugin()

        override fun getPlugins() = listOf(plugin)
    }
}
