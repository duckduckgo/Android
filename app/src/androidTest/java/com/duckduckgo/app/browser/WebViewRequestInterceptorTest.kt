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

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.surrogates.ResourceSurrogates
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockitoAnnotations

class WebViewRequestInterceptorTest {

    private lateinit var testee: WebViewRequestInterceptor

    private var mockTrackerDetector: TrackerDetector = mock()
    private var mockHttpsUpgrader: HttpsUpgrader = mock()
    private var mockResourceSurrogates: ResourceSurrogates = mock()
    private var mockRequest: WebResourceRequest = mock()
    private val mockPrivacyProtectionCountDao: PrivacyProtectionCountDao = mock()

    private lateinit var webView: WebView

    @UiThreadTest
    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        testee = WebViewRequestInterceptor(
            trackerDetector = mockTrackerDetector,
            httpsUpgrader = mockHttpsUpgrader,
            resourceSurrogates = mockResourceSurrogates
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        webView = WebView(context)
    }

    @Test
    fun whenUrlShouldBeUpgradedThenUpgraderInvoked() {
        configureShouldUpgrade()
        testee.shouldIntercept(
            request = mockRequest,
            currentUrl = null,
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        verify(mockHttpsUpgrader).upgrade(any())
    }

    @Test
    fun whenUrlShouldBeUpgradedThenCancelledResponseReturned() {
        configureShouldUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            currentUrl = null,
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        assertCancelledResponse(response)
    }

    @Test
    fun whenUrlShouldBeUpgradedButNotOnMainFrameThenNotUpgraded() {
        configureShouldUpgrade()
        whenever(mockRequest.isForMainFrame).thenReturn(false)
        testee.shouldIntercept(
            request = mockRequest,
            currentUrl = null,
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        verify(mockHttpsUpgrader, never()).upgrade(any())
    }

    @Test
    fun whenUrlShouldBeUpgradedButUrlIsNullThenNotUpgraded() {
        configureShouldUpgrade()
        whenever(mockRequest.url).thenReturn(null)
        testee.shouldIntercept(
            request = mockRequest,
            currentUrl = null,
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        verify(mockHttpsUpgrader, never()).upgrade(any())
    }

    @Test
    fun whenUrlShouldNotBeUpgradedThenUpgraderNotInvoked() {
        whenever(mockHttpsUpgrader.shouldUpgrade(any())).thenReturn(false)
        testee.shouldIntercept(
            request = mockRequest,
            currentUrl = null,
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        verify(mockHttpsUpgrader, never()).upgrade(any())
    }

    @Test
    fun whenCurrentUrlIsNullThenShouldContinueToLoad() {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            currentUrl = null,
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )
        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_DuckDuckGo_ThenShouldContinueToLoad() {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            currentUrl = "duckduckgo.com/a/b/c?q=123",
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_DontTrack_ThenShouldContinueToLoad() {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            currentUrl = "donttrack.us/a/b/c?q=123",
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_SpreadPrivacy_ThenShouldContinueToLoad() {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            currentUrl = "spreadprivacy.com/a/b/c?q=123",
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_DuckDuckHack_ThenShouldContinueToLoad() {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            currentUrl = "duckduckhack.com/a/b/c?q=123",
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_PrivateBrowsingMyths_ThenShouldContinueToLoad() {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            currentUrl = "privatebrowsingmyths.com/a/b/c?q=123",
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_DuckDotCo_ThenShouldContinueToLoad() {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            currentUrl = "duck.co/a/b/c?q=123",
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsHttpRequestThenHttpRequestListenerCalled() {
        configureShouldNotUpgrade()
        whenever(mockRequest.url).thenReturn(Uri.parse("http://example.com"))
        val mockListener = mock<WebViewClientListener>()

        val response = testee.shouldIntercept(
            request = mockRequest,
            currentUrl = "foo.com",
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        verify(mockListener).pageHasHttpResources(anyString())
        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsHttpsRequestThenHttpRequestListenerNotCalled() {
        configureShouldNotUpgrade()
        whenever(mockRequest.url).thenReturn(Uri.parse("https://example.com"))
        val mockListener = mock<WebViewClientListener>()

        val response = testee.shouldIntercept(
            request = mockRequest,
            currentUrl = "foo.com",
            webView = webView,
            webViewClientListener = mockListener,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        verify(mockListener, never()).pageHasHttpResources(anyString())
        assertRequestCanContinueToLoad(response)
    }


    @Test
    fun whenRequestShouldBlockAndNoSurrogateThenCancellingResponseReturned() {
        whenever(mockResourceSurrogates.get(any())).thenReturn(SurrogateResponse(responseAvailable = false))

        configureShouldNotUpgrade()
        configureShouldBlock()
        val response = testee.shouldIntercept(
            request = mockRequest,
            currentUrl = "foo.com",
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        assertCancelledResponse(response)
    }

    @Test
    fun whenRequestShouldBlockButThereIsASurrogateThen() {
        val availableSurrogate = SurrogateResponse(
            responseAvailable = true,
            mimeType = "application/javascript",
            jsFunction = "javascript replacement function goes here"
        )
        whenever(mockResourceSurrogates.get(any())).thenReturn(availableSurrogate)

        configureShouldNotUpgrade()
        configureShouldBlock()
        val response = testee.shouldIntercept(
            request = mockRequest,
            currentUrl = "foo.com",
            webView = webView,
            webViewClientListener = null,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        assertEquals(availableSurrogate.jsFunction.byteInputStream().read(), response!!.data.read())
    }

    private fun assertRequestCanContinueToLoad(response: WebResourceResponse?) {
        assertNull(response)
    }

    private fun configureShouldBlock() {
        val blockTrackingEvent = TrackingEvent(
            blocked = true,
            documentUrl = "",
            trackerUrl = "",
            trackerNetwork = null
        )
        whenever(mockRequest.isForMainFrame).thenReturn(false)
        whenever(mockTrackerDetector.evaluate(any(), any(), any())).thenReturn(blockTrackingEvent)
    }

    private fun configureShouldUpgrade() {
        whenever(mockHttpsUpgrader.shouldUpgrade(any())).thenReturn(true)
        whenever(mockRequest.url).thenReturn(validUri())
        whenever(mockRequest.isForMainFrame).thenReturn(true)
    }

    private fun configureShouldNotUpgrade() {
        whenever(mockHttpsUpgrader.shouldUpgrade(any())).thenReturn(false)

        whenever(mockRequest.url).thenReturn(validUri())
        whenever(mockRequest.isForMainFrame).thenReturn(true)
    }

    private fun validUri() = Uri.parse("example.com")

    private fun assertCancelledResponse(response: WebResourceResponse?) {
        assertNotNull(response)
        assertNull(response!!.data)
        assertNull(response.mimeType)
        assertNull(response.encoding)
    }

}
