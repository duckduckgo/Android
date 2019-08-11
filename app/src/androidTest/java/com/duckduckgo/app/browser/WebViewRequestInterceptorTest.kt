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
import kotlinx.coroutines.runBlocking
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
            resourceSurrogates = mockResourceSurrogates,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext

        webView = WebView(context)
    }

    @Test
    fun whenUrlShouldBeUpgradedThenUpgraderInvoked() = runBlocking<Unit> {
        configureShouldUpgrade()
        testee.shouldIntercept(
            request = mockRequest,
            documentUrl = null,
            webView = webView,
            webViewClientListener = null
        )

        verify(mockHttpsUpgrader).upgrade(any())
    }

    @Test
    fun whenUrlShouldBeUpgradedThenCancelledResponseReturned() = runBlocking<Unit> {
        configureShouldUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUrl = null,
            webView = webView,
            webViewClientListener = null
        )

        assertCancelledResponse(response)
    }

    @Test
    fun whenUrlShouldBeUpgradedButNotOnMainFrameThenNotUpgraded() = runBlocking<Unit> {
        configureShouldUpgrade()
        whenever(mockRequest.isForMainFrame).thenReturn(false)
        testee.shouldIntercept(
            request = mockRequest,
            documentUrl = null,
            webView = webView,
            webViewClientListener = null
        )

        verify(mockHttpsUpgrader, never()).upgrade(any())
    }

    @Test
    fun whenUrlShouldBeUpgradedButUrlIsNullThenNotUpgraded() = runBlocking<Unit> {
        configureShouldUpgrade()
        whenever(mockRequest.url).thenReturn(null)
        testee.shouldIntercept(
            request = mockRequest,
            documentUrl = null,
            webView = webView,
            webViewClientListener = null
        )

        verify(mockHttpsUpgrader, never()).upgrade(any())
    }

    @Test
    fun whenUrlShouldNotBeUpgradedThenUpgraderNotInvoked() = runBlocking<Unit> {
        whenever(mockHttpsUpgrader.shouldUpgrade(any())).thenReturn(false)
        testee.shouldIntercept(
            request = mockRequest,
            documentUrl = null,
            webView = webView,
            webViewClientListener = null
        )

        verify(mockHttpsUpgrader, never()).upgrade(any())
    }

    @Test
    fun whenDocumentUrlIsNullThenShouldContinueToLoad() = runBlocking<Unit> {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUrl = null,
            webView = webView,
            webViewClientListener = null
        )
        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_DuckDuckGo_ThenShouldContinueToLoad() = runBlocking<Unit> {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUrl = "duckduckgo.com/a/b/c?q=123",
            webView = webView,
            webViewClientListener = null
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_DontTrack_ThenShouldContinueToLoad() = runBlocking<Unit> {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUrl = "donttrack.us/a/b/c?q=123",
            webView = webView,
            webViewClientListener = null
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_SpreadPrivacy_ThenShouldContinueToLoad() = runBlocking<Unit> {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUrl = "spreadprivacy.com/a/b/c?q=123",
            webView = webView,
            webViewClientListener = null
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_DuckDuckHack_ThenShouldContinueToLoad() = runBlocking<Unit> {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUrl = "duckduckhack.com/a/b/c?q=123",
            webView = webView,
            webViewClientListener = null
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_PrivateBrowsingMyths_ThenShouldContinueToLoad() = runBlocking<Unit> {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUrl = "privatebrowsingmyths.com/a/b/c?q=123",
            webView = webView,
            webViewClientListener = null
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsTrustedSite_DuckDotCo_ThenShouldContinueToLoad() = runBlocking<Unit> {
        configureShouldNotUpgrade()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUrl = "duck.co/a/b/c?q=123",
            webView = webView,
            webViewClientListener = null
        )

        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsHttpRequestThenHttpRequestListenerCalled() = runBlocking<Unit> {
        configureShouldNotUpgrade()
        whenever(mockRequest.url).thenReturn(Uri.parse("http://example.com"))
        val mockListener = mock<WebViewClientListener>()

        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUrl = "foo.com",
            webView = webView,
            webViewClientListener = mockListener
        )

        verify(mockListener).pageHasHttpResources(anyString())
        assertRequestCanContinueToLoad(response)
    }

    @Test
    fun whenIsHttpsRequestThenHttpRequestListenerNotCalled() = runBlocking<Unit> {
        configureShouldNotUpgrade()
        whenever(mockRequest.url).thenReturn(Uri.parse("https://example.com"))
        val mockListener = mock<WebViewClientListener>()

        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUrl = "foo.com",
            webView = webView,
            webViewClientListener = mockListener
        )

        verify(mockListener, never()).pageHasHttpResources(anyString())
        assertRequestCanContinueToLoad(response)
    }


    @Test
    fun whenRequestShouldBlockAndNoSurrogateThenCancellingResponseReturned() = runBlocking<Unit> {
        whenever(mockResourceSurrogates.get(any())).thenReturn(SurrogateResponse(responseAvailable = false))

        configureShouldNotUpgrade()
        configureShouldBlock()
        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUrl = "foo.com",
            webView = webView,
            webViewClientListener = null
        )

        assertCancelledResponse(response)
    }

    @Test
    fun whenRequestShouldBlockButThereIsASurrogateThen() = runBlocking<Unit> {
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
            documentUrl = "foo.com",
            webView = webView,
            webViewClientListener = null
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

    private fun configureShouldUpgrade() = runBlocking<Unit> {
        whenever(mockHttpsUpgrader.shouldUpgrade(any())).thenReturn(true)
        whenever(mockHttpsUpgrader.upgrade(any())).thenReturn(validHttpsUri())
        whenever(mockRequest.url).thenReturn(validUri())
        whenever(mockRequest.isForMainFrame).thenReturn(true)
    }

    private fun configureShouldNotUpgrade() = runBlocking<Unit> {
        whenever(mockHttpsUpgrader.shouldUpgrade(any())).thenReturn(false)

        whenever(mockRequest.url).thenReturn(validUri())
        whenever(mockRequest.isForMainFrame).thenReturn(true)
    }

    private fun validUri() = Uri.parse("example.com")
    private fun validHttpsUri() = Uri.parse("https://example.com")

    private fun assertCancelledResponse(response: WebResourceResponse?) {
        assertNotNull(response)
        assertNull(response!!.data)
        assertNull(response.mimeType)
        assertNull(response.encoding)
    }

}
