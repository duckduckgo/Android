/*
 * Copyright (c) 2026 DuckDuckGo
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

import android.net.Uri
import android.webkit.WebBackForwardList
import android.webkit.WebHistoryItem
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockerWebViewIntegration
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData.Ignored
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData.MaliciousSite
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.surrogates.ResourceSurrogates
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.CloakedCnameDetector
import com.duckduckgo.app.trackerdetection.WebTrackersBlockedHistory
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TdsEntity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.httpsupgrade.api.HttpsUpgrader
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER
import com.duckduckgo.request.filterer.api.RequestFilterer
import com.duckduckgo.request.interception.api.RequestBlocklist
import com.duckduckgo.request.interception.impl.RequestExemptions
import com.duckduckgo.request.interception.impl.RequestRewriteDecision
import com.duckduckgo.tracker.detection.api.TrackerDetector
import com.duckduckgo.user.agent.api.UserAgentProvider
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class WebViewRequestInterceptorTest {

    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    private lateinit var testee: WebViewRequestInterceptor

    private val mockTrackerDetector: TrackerDetector = mock()
    private val mockHttpsUpgrader: HttpsUpgrader = mock()
    private val mockResourceSurrogates: ResourceSurrogates = mock()
    private val mockRequest: WebResourceRequest = mock()
    private val mockRequestBlocklist: RequestBlocklist = mock()
    private val mockContentBlocking: ContentBlocking = mock()
    private val mockTrackerAllowlist: TrackerAllowlist = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()
    private val mockPrivacyProtectionCountDao: PrivacyProtectionCountDao = mock()
    private val mockGpc: Gpc = mock()
    private val mockAdClickManager: AdClickManager = mock()
    private val mockCloakedCnameDetector: CloakedCnameDetector = mock()
    private val mockRequestFilterer: RequestFilterer = mock()
    private val mockDuckPlayer: DuckPlayer = mock()
    private val mockUserAgentProvider: UserAgentProvider = mock()
    private val mockMaliciousSiteBlockerWebViewIntegration: MaliciousSiteBlockerWebViewIntegration = mock()
    private val fakeAndroidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val mockWebTrackersBlockedHistory: WebTrackersBlockedHistory = mock()
    private val webView: WebView = mock()
    private val mockWebBackForwardList: WebBackForwardList = mock()

    @Before
    fun setup() = runTest {
        whenever(mockMaliciousSiteBlockerWebViewIntegration.shouldIntercept(any(), any(), any())).thenReturn(Ignored)

        testee = WebViewRequestInterceptor(
            trackerDetector = mockTrackerDetector,
            httpsUpgrader = mockHttpsUpgrader,
            resourceSurrogates = mockResourceSurrogates,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao,
            requestRewriteDecision = RequestRewriteDecision(
                gpc = mockGpc,
                userAgentProvider = mockUserAgentProvider,
            ),
            adClickManager = mockAdClickManager,
            cloakedCnameDetector = mockCloakedCnameDetector,
            requestFilterer = mockRequestFilterer,
            requestExemptions = RequestExemptions(
                requestBlocklist = mockRequestBlocklist,
                contentBlocking = mockContentBlocking,
                trackerAllowlist = mockTrackerAllowlist,
                userAllowListRepository = mockUserAllowListRepository,
            ),
            duckPlayer = mockDuckPlayer,
            maliciousSiteBlockerWebViewIntegration = mockMaliciousSiteBlockerWebViewIntegration,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            androidBrowserConfigFeature = fakeAndroidBrowserConfigFeature,
            isMainProcess = true,
            webTrackersBlockedHistory = mockWebTrackersBlockedHistory,
        )
    }

    @Test
    fun whenUrlShouldBeUpgradedThenIncrementUpgradeCountRecordedOnCoroutineDispatch() = runTest {
        whenever(mockRequest.url).thenReturn(validUri())
        whenever(mockRequest.isForMainFrame).thenReturn(true)
        whenever(mockHttpsUpgrader.shouldUpgrade(any())).thenReturn(true)
        whenever(mockHttpsUpgrader.upgrade(any())).thenReturn(validHttpsUri())

        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = null,
        )

        advanceUntilIdle()
        verify(mockPrivacyProtectionCountDao).incrementUpgradeCount()
    }

    @Test
    fun whenInterceptFromServiceWorkerAndRequestIsBlockedWithNoSurrogateThenIncrementBlockedTrackerCountRecordedOnCoroutineDispatch() = runTest {
        whenever(mockRequest.url).thenReturn("tracker.com".toUri())
        whenever(mockRequest.isForMainFrame).thenReturn(false)
        whenever(mockRequest.requestHeaders).thenReturn(emptyMap())
        whenever(mockResourceSurrogates.get(any())).thenReturn(SurrogateResponse(responseAvailable = false))
        whenever(mockTrackerDetector.evaluate(eq("tracker.com".toUri()), eq("foo.com".toUri()), eq(true), any())).thenReturn(blockedTrackingEvent())

        testee.shouldInterceptFromServiceWorker(
            request = mockRequest,
            documentUrl = "foo.com".toUri(),
        )

        advanceUntilIdle()
        verify(mockPrivacyProtectionCountDao).incrementBlockedTrackerCount()
    }

    @Test
    fun whenInterceptFromServiceWorkerAndRequestIsBlockedThenTrackerBlockedRecordedOnCoroutineDispatchWithCorrectValues() = runTest {
        whenever(mockRequest.url).thenReturn("tracker.com".toUri())
        whenever(mockRequest.isForMainFrame).thenReturn(false)
        whenever(mockRequest.requestHeaders).thenReturn(emptyMap())
        whenever(mockResourceSurrogates.get(any())).thenReturn(SurrogateResponse(responseAvailable = false))
        whenever(mockTrackerDetector.evaluate(eq("tracker.com".toUri()), eq("foo.com".toUri()), eq(true), any()))
            .thenReturn(blockedTrackingEvent())

        testee.shouldInterceptFromServiceWorker(
            request = mockRequest,
            documentUrl = "foo.com".toUri(),
        )

        advanceUntilIdle()

        verify(mockWebTrackersBlockedHistory).onTrackerBlocked(
            trackerUrl = "tracker.com",
            trackerCompany = "Tracker Inc",
        )
    }

    private fun blockedTrackingEvent() = TrackingEvent(
        documentUrl = "foo.com",
        trackerUrl = "tracker.com",
        categories = null,
        entity = TdsEntity("Tracker Inc", "Tracker Inc", 10.0),
        surrogateId = null,
        status = TrackerStatus.BLOCKED,
        type = TrackerType.OTHER,
    )

    private fun validUri(): Uri = "example.com".toUri()
    private fun validHttpsUri(): Uri = "https://example.com".toUri()

    @Before
    fun configureWebView() = runTest {
        whenever(mockMaliciousSiteBlockerWebViewIntegration.shouldIntercept(any(), anyOrNull(), any())).thenReturn(Ignored)
        configureUserAgent()
        configureStack()
    }

    @Test
    fun whenUrlShouldBeFilteredThenResponseIsCancelled() = runTest {
        whenever(mockRequest.url).thenReturn("foo.com".toUri())
        whenever(mockRequestFilterer.shouldFilterOutRequest(mockRequest, "foo.com")).thenReturn(true)

        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )
        assertCancelledResponse(response)
    }

    @Test
    fun whenUrlShouldBeUpgradedThenUpgraderInvoked() = runTest {
        configureShouldUpgrade()
        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = null,
        )

        verify(mockHttpsUpgrader).upgrade(any())
    }

    @Test
    fun whenUrlShouldBeUpgradedSiteIsMaliciousAndUpgradeBeforeMaliciousEnabledThenUpgrade() = runTest {
        configureShouldUpgrade()
        whenever(mockMaliciousSiteBlockerWebViewIntegration.shouldIntercept(any(), anyOrNull(), any())).thenReturn(maliciousSite())
        fakeAndroidBrowserConfigFeature.checkMaliciousAfterHttpsUpgrade().setRawStoredState(State(true))
        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = null,
        )

        verify(mockHttpsUpgrader).upgrade(any())
    }

    @Test
    fun whenUrlShouldBeUpgradedSiteIsMaliciousAndUpgradeBeforeMaliciousDisabledThenDontUpgrade() = runTest {
        configureShouldUpgrade()
        whenever(mockMaliciousSiteBlockerWebViewIntegration.shouldIntercept(any(), anyOrNull(), any())).thenReturn(maliciousSite())
        fakeAndroidBrowserConfigFeature.checkMaliciousAfterHttpsUpgrade().setRawStoredState(State(false))
        testee = WebViewRequestInterceptor(
            trackerDetector = mockTrackerDetector,
            httpsUpgrader = mockHttpsUpgrader,
            resourceSurrogates = mockResourceSurrogates,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao,
            requestRewriteDecision = RequestRewriteDecision(
                gpc = mockGpc,
                userAgentProvider = mockUserAgentProvider,
            ),
            adClickManager = mockAdClickManager,
            cloakedCnameDetector = mockCloakedCnameDetector,
            requestFilterer = mockRequestFilterer,
            requestExemptions = RequestExemptions(
                requestBlocklist = mockRequestBlocklist,
                contentBlocking = mockContentBlocking,
                trackerAllowlist = mockTrackerAllowlist,
                userAllowListRepository = mockUserAllowListRepository,
            ),
            duckPlayer = mockDuckPlayer,
            maliciousSiteBlockerWebViewIntegration = mockMaliciousSiteBlockerWebViewIntegration,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            androidBrowserConfigFeature = fakeAndroidBrowserConfigFeature,
            isMainProcess = true,
            webTrackersBlockedHistory = mockWebTrackersBlockedHistory,
        )
        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = null,
        )

        verify(mockHttpsUpgrader, never()).upgrade(any())
    }

    @Test
    fun whenUrlShouldBeUpgradedThenCancelledResponseReturned() = runTest {
        configureShouldUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = null,
        )

        assertCancelledResponse(response)
    }

    @Test
    fun whenUrlShouldBeUpgradedButNotOnMainFrameThenNotUpgraded() = runTest {
        configureShouldUpgrade()
        whenever(mockRequest.isForMainFrame).thenReturn(false)
        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = null,
        )

        verify(mockHttpsUpgrader, never()).upgrade(any())
    }

    @Test
    fun whenInterceptUrlAndShouldUpgradeThenShouldUpgradeIsCalledAndNotDuckPlayer() = runTest {
        configureShouldUpgrade()
        configureDuckPlayer()
        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = null,
        )
        verify(mockHttpsUpgrader).upgrade(any())
        verify(mockDuckPlayer, never()).intercept(any(), any(), any())
    }

    @Test
    fun whenInterceptUrlDuckPlayerInterceptIsCalled() = runTest {
        configureDuckPlayer()
        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = null,
        )
        verify(mockDuckPlayer).intercept(any(), any(), any())
    }

    @Test
    fun whenUrlShouldNotBeUpgradedThenUpgraderNotInvoked() = runTest {
        whenever(mockRequest.url).thenReturn(validUri())
        whenever(mockHttpsUpgrader.shouldUpgrade(any())).thenReturn(false)
        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = null,
        )

        verify(mockHttpsUpgrader, never()).upgrade(any())
    }

    @Test
    fun whenDocumentUrlIsNullThenShouldContinueToLoad() = runTest {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = null,
        )
        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_DuckDuckGo_ThenShouldContinueToLoad() = runTest {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "duckduckgo.com/a/b/c?q=123".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_DontTrack_ThenShouldContinueToLoad() = runTest {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "donttrack.us/a/b/c?q=123".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_SpreadPrivacy_ThenShouldContinueToLoad() = runTest {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "spreadprivacy.com/a/b/c?q=123".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_DuckDuckHack_ThenShouldContinueToLoad() = runTest {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "duckduckhack.com/a/b/c?q=123".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_PrivateBrowsingMyths_ThenShouldContinueToLoad() = runTest {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "privatebrowsingmyths.com/a/b/c?q=123".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_DuckDotCo_ThenShouldContinueToLoad() = runTest {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "duck.co/a/b/c?q=123".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsHttpRequestThenHttpRequestListenerCalled() = runTest {
        configureShouldNotUpgrade()
        whenever(mockRequest.url).thenReturn(Uri.parse("http://example.com"))
        val mockListener = mock<WebViewClientListener>()

        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = mockListener,
        )

        verify(mockListener).pageHasHttpResources(any<Uri>())
        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsHttpsRequestThenHttpRequestListenerNotCalled() = runTest {
        configureShouldNotUpgrade()
        whenever(mockRequest.url).thenReturn(Uri.parse("https://example.com"))
        val mockListener = mock<WebViewClientListener>()

        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = mockListener,
        )

        verify(mockListener, never()).pageHasHttpResources(anyString())
        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenRequestShouldBlockAndNoSurrogateThenCancellingResponseReturned() = runTest {
        whenever(mockResourceSurrogates.get(any())).thenReturn(SurrogateResponse(responseAvailable = false))

        configureShouldNotUpgrade()
        configureShouldBlock()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertCancelledResponse(response)
    }

    @Test
    fun whenRequestShouldBlockButThereIsASurrogateThenResponseReturnedContainsTheSurrogateData() = runTest {
        val availableSurrogate = SurrogateResponse(
            scriptId = "testId",
            responseAvailable = true,
            mimeType = "application/javascript",
            jsFunction = "javascript replacement function goes here",
        )
        whenever(mockResourceSurrogates.get(any())).thenReturn(availableSurrogate)

        configureShouldNotUpgrade()
        configureShouldBlock()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertEquals(availableSurrogate.jsFunction.byteInputStream().read(), response!!.data.read())
    }

    @Test
    fun whenRequestShouldBlockButThereIsASurrogateThenCallSurrogateDetected() = runTest {
        val availableSurrogate = SurrogateResponse(
            scriptId = "testId",
            responseAvailable = true,
            mimeType = "application/javascript",
            jsFunction = "javascript replacement function goes here",
        )
        val mockWebViewClientListener: WebViewClientListener = mock()
        whenever(mockResourceSurrogates.get(any())).thenReturn(availableSurrogate)

        configureShouldNotUpgrade()
        configureShouldBlock()
        testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = mockWebViewClientListener,
        )

        verify(mockWebViewClientListener).surrogateDetected(availableSurrogate)
    }

    @Test
    fun whenUrlShouldBeUpgradedThenNotifyWebViewClientListener() = runTest {
        configureShouldUpgrade()
        val mockWebViewClientListener: WebViewClientListener = mock()
        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener,
        )

        verify(mockWebViewClientListener).upgradedToHttps()
    }

    @Test
    fun whenUrlShouldBeUpgradedAndGcpActiveThenLoadUrlWithGpcHeaders() = runTest {
        configureShouldUpgrade()
        configureShouldAddGpcHeader()
        val mockWebViewClientListener: WebViewClientListener = mock()

        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener,
        )

        verify(webView).loadUrl(validHttpsUri().toString(), mockGpc.getHeaders(validHttpsUri().toString()))
    }

    @Test
    fun whenRequestShouldAddGcpHeadersThenRedirectTriggeredByGpcCalled() = runTest {
        configureShouldNotUpgrade()
        configureShouldAddGpcHeader()
        configureUrlDoesNotExistInTheStack()
        val mockWebViewClientListener: WebViewClientListener = mock()
        whenever(mockWebViewClientListener.isDesktopSiteEnabled(any())).thenReturn(false)

        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener,
        )

        verify(mockWebViewClientListener).redirectTriggeredByGpc()
    }

    @Test
    fun whenRequestShouldAddGcpHeadersThenLoadUrlWithGpcHeaders() = runTest {
        configureShouldNotUpgrade()
        configureShouldAddGpcHeader()
        configureUrlDoesNotExistInTheStack()
        val mockWebViewClientListener: WebViewClientListener = mock()
        whenever(mockWebViewClientListener.isDesktopSiteEnabled(any())).thenReturn(false)

        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener,
        )

        verify(webView).loadUrl(validUri().toString(), mockGpc.getHeaders(validUri().toString()))
    }

    @Test
    fun whenRequestShouldAddGcpHeadersButUrlExistsInTheStackThenLoadUrlNotCalled() = runTest {
        configureShouldNotUpgrade()
        configureShouldAddGpcHeader()
        configureUrlExistsInTheStack()
        val mockWebViewClientListener: WebViewClientListener = mock()

        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener,
        )

        verify(webView, never()).loadUrl(any(), any())
    }

    @Test
    fun whenRequestShouldAddGcpHeadersButAlreadyContainsHeadersThenLoadUrlNotCalled() = runTest {
        configureShouldNotUpgrade()
        configureRequestContainsGcpHeader()

        val mockWebViewClientListener: WebViewClientListener = mock()

        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener,
        )

        verify(webView, never()).loadUrl(any(), any())
    }

    @Test
    fun whenRequestShouldNotAddGcpHeadersThenLoadUrlNotCalled() = runTest {
        configureShouldNotUpgrade()
        configureShouldNotAddGpcHeader()
        val mockWebViewClientListener: WebViewClientListener = mock()

        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener,
        )

        verify(webView, never()).loadUrl(any(), any())
    }

    @Test
    fun whenUserAgentShouldChangeThenReloadUrl() = runTest {
        configureUserAgentShouldChange()
        configureUrlDoesNotExistInTheStack()

        val mockWebViewClientListener: WebViewClientListener = mock()
        whenever(mockWebViewClientListener.isDesktopSiteEnabled(any())).thenReturn(false)
        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener,
        )

        verify(webView).loadUrl(any(), any())
    }

    @Test
    fun whenUserAgentShouldChangeAndUrlAlreadyWasInTheStackButIsNotTheLastElementThenDoNotReloadUrl() = runTest {
        configureUserAgentShouldChange()
        configureUrlExistsInTheStack("https://m.facebook.com".toUri())

        val mockWebViewClientListener: WebViewClientListener = mock()
        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener,
        )

        verify(webView, never()).loadUrl(any(), any())
    }

    @Test
    fun whenUserAgentHasNotChangedThenDoNotReloadUrl() = runTest {
        configureShouldNotUpgrade()
        configureUrlDoesNotExistInTheStack()

        val mockWebViewClientListener: WebViewClientListener = mock()
        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener,
        )

        verify(webView, never()).loadUrl(any(), any())
    }

    @Test
    fun whenInterceptFromServiceWorkerAndRequestShouldBlockAndNoSurrogateThenCancellingResponseReturned() = runTest {
        whenever(mockResourceSurrogates.get(any())).thenReturn(SurrogateResponse(responseAvailable = false))

        configureShouldNotUpgrade()
        configureShouldBlock()
        val response = testee.shouldInterceptFromServiceWorker(
            request = mockRequest,
            documentUrl = "foo.com".toUri(),
        )

        assertCancelledResponse(response)
    }

    @Test
    fun whenInterceptFromServiceWorkerAndRequestShouldBlockButThereIsASurrogateThenResponseReturnedContainsTheSurrogateData() = runTest {
        val availableSurrogate = SurrogateResponse(
            scriptId = "testId",
            responseAvailable = true,
            mimeType = "application/javascript",
            jsFunction = "javascript replacement function goes here",
        )
        whenever(mockResourceSurrogates.get(any())).thenReturn(availableSurrogate)

        configureShouldNotUpgrade()
        configureShouldBlock()
        val response = testee.shouldInterceptFromServiceWorker(
            request = mockRequest,
            documentUrl = "foo.com".toUri(),
        )

        assertEquals(availableSurrogate.jsFunction.byteInputStream().read(), response!!.data.read())
    }

    @Test
    fun whenInterceptFromServiceWorkerAndRequestIsNullThenReturnNull() = runTest {
        assertNull(testee.shouldInterceptFromServiceWorker(request = null, documentUrl = "foo.com".toUri()))
    }

    @Test
    fun whenInterceptFromServiceWorkerAndDocumentUrlIsNullThenReturnNull() = runTest {
        assertNull(testee.shouldInterceptFromServiceWorker(request = mockRequest, documentUrl = null))
    }

    @Test
    fun whenInterceptFromServiceWorkerAndDirectTrackerBlockedThenInsertIntoWebTrackersBlockedHistory() = runTest {
        whenever(mockResourceSurrogates.get(any())).thenReturn(SurrogateResponse(responseAvailable = false))
        whenever(mockRequest.url).thenReturn("foo.com".toUri())
        whenever(mockRequest.requestHeaders).thenReturn(emptyMap())
        configureShouldBlock()

        testee.shouldInterceptFromServiceWorker(
            request = mockRequest,
            documentUrl = "foo.com".toUri(),
        )

        verify(mockWebTrackersBlockedHistory).onTrackerBlocked(any(), any())
    }

    @Test
    fun whenInterceptFromServiceWorkerAndCloakedCnameTrackerBlockedThenInsertIntoWebTrackersBlockedHistory() = runTest {
        whenever(mockRequest.requestHeaders).thenReturn(emptyMap())
        configureNull()
        configureBlockedCnameTrackingEvent(trackerUrl = "uncloaked-host.com")

        val uri = "host.com".toUri()
        whenever(mockRequest.url).thenReturn(uri)
        whenever(mockCloakedCnameDetector.detectCnameCloakedHost(anyString(), any())).thenReturn("uncloaked-host.com")

        testee.shouldInterceptFromServiceWorker(
            request = mockRequest,
            documentUrl = "foo.com".toUri(),
        )

        val captor = argumentCaptor<String>()
        verify(mockWebTrackersBlockedHistory).onTrackerBlocked(captor.capture(), any())
        assertEquals("uncloaked-host.com", captor.firstValue)
    }

    @Test
    fun whenIsAppUrlPixelThenShouldContinueToLoad() = runTest {
        whenever(mockRequest.url).thenReturn(
            "https://improving.duckduckgo.com/t/m_nav_nt_p_android_phone?atb=v336-7&appVersion=5.131.0&test=1".toUri(),
        )
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenTrackingEventIsNullAndUncloakedHostFoundAndIsTrackerThenBlockRequest() = runTest {
        configureNull()
        assertRequestBlockedWhenUncloakedHostFound()
    }

    @Test
    fun whenTrackingEventIsAllowedAndUncloakedHostFoundAndIsTrackerThenBlockRequest() = runTest {
        configureShouldAllow()
        assertRequestBlockedWhenUncloakedHostFound()
    }

    @Test
    fun whenTrackingEventIsSameEntityAllowedAndUncloakedHostFoundAndIsTrackerThenBlockRequest() = runTest {
        configureSameEntity()
        assertRequestBlockedWhenUncloakedHostFound()
    }

    @Test
    fun whenTrackingEventIsNotBlockedAndUncloakedHostNotFoundThenContinueToLoad() = runTest {
        configureNull()
        configureShouldNotUpgrade()
        configureBlockedCnameTrackingEvent()

        val uri = "host.com".toUri()
        whenever(mockRequest.url).thenReturn(uri)
        whenever(mockCloakedCnameDetector.detectCnameCloakedHost(anyString(), any())).thenReturn(null)

        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        verify(mockCloakedCnameDetector).detectCnameCloakedHost("foo.com", uri)
        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenTrackingEventIsNotBlockedAndUncloakedHostFoundButIsNotATrackerThenContinueToLoad() = runTest {
        configureNull()
        configureShouldNotUpgrade()
        configureAllowedCnameTrackingEvent()

        val uri = "host.com".toUri()
        whenever(mockRequest.url).thenReturn(uri)
        whenever(mockCloakedCnameDetector.detectCnameCloakedHost(anyString(), any())).thenReturn("uncloaked-host.com")

        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        verify(mockCloakedCnameDetector).detectCnameCloakedHost("foo.com", uri)
        assertRequestCanContinueToLoad(response)
    }

    private suspend fun assertRequestBlockedWhenUncloakedHostFound() {
        configureShouldNotUpgrade()
        configureBlockedCnameTrackingEvent()

        val uri = "host.com".toUri()
        whenever(mockRequest.url).thenReturn(uri)
        whenever(mockCloakedCnameDetector.detectCnameCloakedHost(anyString(), any())).thenReturn("uncloaked-host.com")

        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        verify(mockCloakedCnameDetector).detectCnameCloakedHost("foo.com", uri)
        assertCancelledResponse(response)
    }

    @Test
    fun whenCloakedCnameTrackerIsBlockedThenRecordTrackerBlockedWithUncloakedUrl() = runTest {
        configureNull()
        configureShouldNotUpgrade()
        configureBlockedCnameTrackingEvent(trackerUrl = "uncloaked-host.com", entity = TdsEntity("Tracker Inc", "Tracker Inc", 10.0))

        val uri = "host.com".toUri()
        whenever(mockRequest.url).thenReturn(uri)
        whenever(mockCloakedCnameDetector.detectCnameCloakedHost(anyString(), any())).thenReturn("uncloaked-host.com")

        testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        verify(mockWebTrackersBlockedHistory).onTrackerBlocked(
            trackerUrl = "uncloaked-host.com",
            trackerCompany = "Tracker Inc",
        )
    }

    @Test
    fun whenRequestIsInBlocklistAndNotMainFrameAndNotAllowlistedThenBlockRequest() = runTest {
        configureShouldNotUpgrade()
        configureSubframeRequest()
        configureRequestInBlocklist()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertCancelledResponse(response)
    }

    @Test
    fun whenRequestIsInBlocklistButIsContentBlockingExceptionThenContinueToLoad() = runTest {
        configureShouldNotUpgrade()
        configureSubframeRequest()
        configureRequestInBlocklist()
        whenever(mockContentBlocking.isAnException(anyString())).thenReturn(true)
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenRequestIsInBlocklistButIsInTrackerAllowlistThenContinueToLoad() = runTest {
        configureShouldNotUpgrade()
        configureSubframeRequest()
        configureRequestInBlocklist()
        whenever(mockTrackerAllowlist.isAnException(anyString(), anyString())).thenReturn(true)
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenRequestIsInBlocklistButIsUserAllowlistedThenContinueToLoad() = runTest {
        configureShouldNotUpgrade()
        configureSubframeRequest()
        configureRequestInBlocklist()
        whenever(mockUserAllowListRepository.isUriInUserAllowList(any())).thenReturn(true)
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenRequestIsNotInBlocklistThenContinueToLoad() = runTest {
        configureShouldNotUpgrade()
        configureSubframeRequest()
        whenever(mockRequestBlocklist.containedInBlocklist(any(), any())).thenReturn(false)
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        assertRequestCanContinueToLoad(response)
    }

    private fun assertRequestCanContinueToLoad(response: WebResourceResponse?) {
        assertNull(response)
    }

    private fun configureSubframeRequest() {
        whenever(mockRequest.isForMainFrame).thenReturn(false)
    }

    private fun configureRequestInBlocklist() {
        whenever(mockRequestBlocklist.containedInBlocklist(any(), any())).thenReturn(true)
    }

    private fun configureShouldBlock() {
        configureTrackingEvent(TrackerStatus.BLOCKED)
    }

    private fun configureShouldAllow() {
        configureTrackingEvent(TrackerStatus.ALLOWED)
    }

    private fun configureSameEntity() {
        configureTrackingEvent(TrackerStatus.SAME_ENTITY_ALLOWED)
    }

    private fun configureTrackingEvent(status: TrackerStatus) {
        val trackingEvent = TrackingEvent(
            status = status,
            type = TrackerType.OTHER,
            documentUrl = "",
            trackerUrl = "",
            entity = null,
            categories = null,
            surrogateId = "testId",
        )
        whenever(mockRequest.isForMainFrame).thenReturn(false)
        whenever(mockTrackerDetector.evaluate(anyString(), any<Uri>(), eq(true), anyMap())).thenReturn(trackingEvent)
        whenever(mockTrackerDetector.evaluate(any<Uri>(), any<Uri>(), eq(true), anyMap())).thenReturn(trackingEvent)
    }

    private fun configureNull() {
        whenever(mockRequest.isForMainFrame).thenReturn(false)
        whenever(mockTrackerDetector.evaluate(anyString(), any<Uri>(), eq(true), anyMap())).thenReturn(null)
    }

    private fun configureBlockedCnameTrackingEvent(trackerUrl: String = "", entity: Entity? = null) {
        configureCnameTrackingEvent(TrackerStatus.BLOCKED, trackerUrl = trackerUrl, entity = entity)
    }

    private fun configureAllowedCnameTrackingEvent() {
        configureCnameTrackingEvent(TrackerStatus.ALLOWED)
    }

    private fun configureCnameTrackingEvent(status: TrackerStatus, trackerUrl: String = "", entity: Entity? = null) {
        val trackingEvent = TrackingEvent(
            status = status,
            type = TrackerType.OTHER,
            documentUrl = "",
            trackerUrl = trackerUrl,
            entity = entity,
            categories = null,
            surrogateId = null,
        )
        whenever(mockRequest.isForMainFrame).thenReturn(false)
        whenever(mockTrackerDetector.evaluate(anyString(), any<Uri>(), eq(false), anyMap())).thenReturn(trackingEvent)
    }

    private fun configureUrlExistsInTheStack(uri: Uri = validUri()) {
        val mockWebHistoryItem: WebHistoryItem = mock()
        whenever(mockWebHistoryItem.url).thenReturn(uri.toString())
        whenever(mockWebBackForwardList.currentItem).thenReturn(mockWebHistoryItem)
        whenever(webView.copyBackForwardList()).thenReturn(mockWebBackForwardList)
    }

    private fun configureUrlDoesNotExistInTheStack() {
        val mockWebHistoryItem: WebHistoryItem = mock()
        whenever(mockWebHistoryItem.url).thenReturn("www.test.com")
        whenever(mockWebBackForwardList.currentItem).thenReturn(mockWebHistoryItem)
        whenever(webView.copyBackForwardList()).thenReturn(mockWebBackForwardList)
    }

    private fun configureStack() {
        configureUrlExistsInTheStack()
    }

    private fun configureRequestContainsGcpHeader() = runTest {
        whenever(mockGpc.isEnabled()).thenReturn(true)
        whenever(mockRequest.method).thenReturn("GET")
        whenever(mockRequest.requestHeaders).thenReturn(mapOf(GPC_HEADER to "test"))
    }

    private fun configureShouldAddGpcHeader() = runTest {
        whenever(mockGpc.isEnabled()).thenReturn(true)
        whenever(mockGpc.getHeaders(anyString())).thenReturn(mapOf("test" to "test"))
        whenever(mockGpc.canUrlAddHeaders(any(), any())).thenReturn(true)
        whenever(mockRequest.method).thenReturn("GET")
    }

    private fun configureShouldNotAddGpcHeader() = runTest {
        whenever(mockGpc.isEnabled()).thenReturn(false)
        whenever(mockGpc.getHeaders(anyString())).thenReturn(mapOf("test" to "test"))
        whenever(mockGpc.canUrlAddHeaders(any(), any())).thenReturn(false)
        whenever(mockRequest.method).thenReturn("GET")
    }

    private fun configureUserAgentShouldChange() = runTest {
        whenever(mockRequest.url).thenReturn(Uri.parse("https://m.facebook.com"))
        whenever(mockRequest.isForMainFrame).thenReturn(true)
        whenever(mockRequest.method).thenReturn("GET")
        whenever(mockUserAgentProvider.userAgent(any(), any())).thenReturn(NEW_USER_AGENT)
    }

    private fun configureShouldUpgrade() = runTest {
        whenever(mockHttpsUpgrader.shouldUpgrade(any())).thenReturn(true)
        whenever(mockHttpsUpgrader.upgrade(any())).thenReturn(validHttpsUri())
        whenever(mockRequest.url).thenReturn(validUri())
        whenever(mockRequest.isForMainFrame).thenReturn(true)
    }

    private fun configureDuckPlayer() = runTest {
        whenever(mockRequest.url).thenReturn(validUri())
        whenever(mockDuckPlayer.intercept(any(), any(), any())).thenReturn(mock())
    }

    private fun configureShouldNotUpgrade() = runTest {
        whenever(mockHttpsUpgrader.shouldUpgrade(any())).thenReturn(false)

        whenever(mockRequest.url).thenReturn(validUri())
        whenever(mockRequest.isForMainFrame).thenReturn(true)
    }

    private fun assertCancelledResponse(response: WebResourceResponse?) {
        assertNotNull(response)
        assertNull(response!!.data)
        assertNull(response.mimeType)
        assertNull(response.encoding)
    }

    private fun configureUserAgent() {
        val settings: WebSettings = mock()
        whenever(webView.settings).thenReturn(settings)
        whenever(settings.userAgentString).thenReturn(CURRENT_USER_AGENT)
        whenever(mockUserAgentProvider.userAgent(any(), any())).thenReturn(CURRENT_USER_AGENT)
    }

    private fun maliciousSite() = MaliciousSite("foo.com".toUri(), Feed.MALWARE, false, true)

    private companion object {
        const val CURRENT_USER_AGENT = "current-agent"
        const val NEW_USER_AGENT = "new-agent"
    }

    @Test
    fun whenCloakedCnameUncloakingRunsThenTrackerDetectedIsCalledTwice() = runTest {
        configureShouldNotUpgrade()
        configureShouldAllow()
        configureBlockedCnameTrackingEvent()
        whenever(mockCloakedCnameDetector.detectCnameCloakedHost(anyString(), any())).thenReturn("uncloaked-host.com")
        val mockWebViewClientListener: WebViewClientListener = mock()

        testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = mockWebViewClientListener,
        )

        verify(mockWebViewClientListener, times(2)).trackerDetected(any())
    }

    @Test
    fun whenRequestIsBlockedWithSurrogateThenBlockedTrackerCountNotIncremented() = runTest {
        val availableSurrogate = SurrogateResponse(
            scriptId = "testId",
            responseAvailable = true,
            mimeType = "application/javascript",
            jsFunction = "javascript replacement function goes here",
        )
        whenever(mockResourceSurrogates.get(any())).thenReturn(availableSurrogate)
        configureShouldNotUpgrade()
        configureShouldBlock()

        testee.shouldIntercept(
            request = mockRequest,
            documentUri = "foo.com".toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        advanceUntilIdle()
        verify(mockPrivacyProtectionCountDao, never()).incrementBlockedTrackerCount()
    }

    @Test
    fun whenRequestShouldAddGpcHeadersThenGpcHeadersAreAddedToTheRequestHeaders() = runTest {
        configureShouldNotUpgrade()
        configureShouldAddGpcHeader()
        configureUrlDoesNotExistInTheStack()
        val requestHeaders = mutableMapOf("existing" to "header")
        whenever(mockRequest.requestHeaders).thenReturn(requestHeaders)
        val mockWebViewClientListener: WebViewClientListener = mock()
        whenever(mockWebViewClientListener.isDesktopSiteEnabled(any())).thenReturn(false)

        testee.shouldIntercept(
            request = mockRequest,
            documentUri = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener,
        )

        assertEquals(mapOf("existing" to "header", "test" to "test"), mockRequest.requestHeaders)
    }
}
