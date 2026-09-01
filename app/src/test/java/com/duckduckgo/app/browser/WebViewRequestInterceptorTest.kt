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
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockerWebViewIntegration
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData.Ignored
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.surrogates.ResourceSurrogates
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.CloakedCnameDetector
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import com.duckduckgo.app.trackerdetection.db.WebTrackersBlockedDao
import com.duckduckgo.app.trackerdetection.model.TdsEntity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.httpsupgrade.api.HttpsUpgrader
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import com.duckduckgo.request.filterer.api.RequestFilterer
import com.duckduckgo.request.interception.api.RequestBlocklist
import com.duckduckgo.tracker.detection.api.TrackerDetector
import com.duckduckgo.user.agent.api.UserAgentProvider
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
    private val mockWebTrackersBlockedDao: WebTrackersBlockedDao = mock()
    private val webView: WebView = mock()

    @Before
    fun setup() = runTest {
        whenever(mockMaliciousSiteBlockerWebViewIntegration.shouldIntercept(any(), any(), any())).thenReturn(Ignored)

        testee = WebViewRequestInterceptor(
            trackerDetector = mockTrackerDetector,
            httpsUpgrader = mockHttpsUpgrader,
            resourceSurrogates = mockResourceSurrogates,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao,
            gpc = mockGpc,
            userAgentProvider = mockUserAgentProvider,
            adClickManager = mockAdClickManager,
            cloakedCnameDetector = mockCloakedCnameDetector,
            requestFilterer = mockRequestFilterer,
            requestBlocklist = mockRequestBlocklist,
            contentBlocking = mockContentBlocking,
            trackerAllowlist = mockTrackerAllowlist,
            userAllowListRepository = mockUserAllowListRepository,
            duckPlayer = mockDuckPlayer,
            maliciousSiteBlockerWebViewIntegration = mockMaliciousSiteBlockerWebViewIntegration,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            androidBrowserConfigFeature = fakeAndroidBrowserConfigFeature,
            isMainProcess = true,
            webTrackersBlockedDao = mockWebTrackersBlockedDao,
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
    fun whenInterceptFromServiceWorkerAndRequestIsBlockedThenWebTrackerBlockedInsertedOnCoroutineDispatchWithCorrectValues() = runTest {
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

        val captor = argumentCaptor<WebTrackerBlocked>()
        verify(mockWebTrackersBlockedDao).insert(captor.capture())
        assertEquals("tracker.com", captor.firstValue.trackerUrl)
        assertEquals("Tracker Inc", captor.firstValue.trackerCompany)
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
}
