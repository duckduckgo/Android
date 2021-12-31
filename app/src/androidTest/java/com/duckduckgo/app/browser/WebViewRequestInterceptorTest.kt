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
import android.webkit.*
import androidx.core.net.toUri
import androidx.test.annotation.UiThreadTest
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.useragent.UserAgentProvider
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.surrogates.ResourceSurrogates
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.impl.features.gpc.RealGpc.Companion.GPC_HEADER
import org.mockito.kotlin.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class WebViewRequestInterceptorTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: WebViewRequestInterceptor

    private var mockTrackerDetector: TrackerDetector = mock()
    private var mockHttpsUpgrader: HttpsUpgrader = mock()
    private var mockResourceSurrogates: ResourceSurrogates = mock()
    private var mockRequest: WebResourceRequest = mock()
    private val mockPrivacyProtectionCountDao: PrivacyProtectionCountDao = mock()
    private val mockGpc: Gpc = mock()
    private val mockWebBackForwardList: WebBackForwardList = mock()
    private val userAgentProvider: UserAgentProvider = UserAgentProvider({ DEFAULT }, mock())

    private var webView: WebView = mock()

    @UiThreadTest
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        configureUserAgent()
        configureStack()

        testee = WebViewRequestInterceptor(
            trackerDetector = mockTrackerDetector,
            httpsUpgrader = mockHttpsUpgrader,
            resourceSurrogates = mockResourceSurrogates,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao,
            gpc = mockGpc,
            userAgentProvider = userAgentProvider
        )
    }

    @Test
    fun whenUrlShouldBeUpgradedThenUpgraderInvoked() = runTest {
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
    fun whenUrlShouldBeUpgradedThenCancelledResponseReturned() = runTest {
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
    fun whenUrlShouldBeUpgradedButNotOnMainFrameThenNotUpgraded() = runTest {
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
    fun whenUrlShouldBeUpgradedButUrlIsNullThenNotUpgraded() = runTest {
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
    fun whenUrlShouldNotBeUpgradedThenUpgraderNotInvoked() = runTest {
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
    fun whenDocumentUrlIsNullThenShouldContinueToLoad() = runTest {
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
    fun whenIsTrustedSite_DuckDuckGo_ThenShouldContinueToLoad() = runTest {
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
    fun whenIsTrustedSite_DontTrack_ThenShouldContinueToLoad() = runTest {
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
    fun whenIsTrustedSite_SpreadPrivacy_ThenShouldContinueToLoad() = runTest {
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
    fun whenIsTrustedSite_DuckDuckHack_ThenShouldContinueToLoad() = runTest {
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
    fun whenIsTrustedSite_PrivateBrowsingMyths_ThenShouldContinueToLoad() = runTest {
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
    fun whenIsTrustedSite_DuckDotCo_ThenShouldContinueToLoad() = runTest {
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
    fun whenIsHttpRequestThenHttpRequestListenerCalled() = runTest {
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
    fun whenIsHttpsRequestThenHttpRequestListenerNotCalled() = runTest {
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
    fun whenRequestShouldBlockAndNoSurrogateThenCancellingResponseReturned() = runTest {
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
    fun whenRequestShouldBlockButThereIsASurrogateThenResponseReturnedContainsTheSurrogateData() = runTest {
        val availableSurrogate = SurrogateResponse(
            scriptId = "testId",
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

    @Test
    fun whenRequestShouldBlockButThereIsASurrogateThenCallSurrogateDetected() = runTest {
        val availableSurrogate = SurrogateResponse(
            scriptId = "testId",
            responseAvailable = true,
            mimeType = "application/javascript",
            jsFunction = "javascript replacement function goes here"
        )
        val mockWebViewClientListener: WebViewClientListener = mock()
        whenever(mockResourceSurrogates.get(any())).thenReturn(availableSurrogate)

        configureShouldNotUpgrade()
        configureShouldBlock()
        testee.shouldIntercept(
            request = mockRequest,
            documentUrl = "foo.com",
            webView = webView,
            webViewClientListener = mockWebViewClientListener
        )

        verify(mockWebViewClientListener).surrogateDetected(availableSurrogate)
    }

    @Test
    fun whenUrlShouldBeUpgradedThenNotifyWebViewClientListener() = runTest {
        configureShouldUpgrade()
        val mockWebViewClientListener: WebViewClientListener = mock()
        testee.shouldIntercept(
            request = mockRequest,
            documentUrl = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener
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
            documentUrl = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener
        )

        verify(webView).loadUrl(validHttpsUri().toString(), mockGpc.getHeaders(validHttpsUri().toString()))
    }

    @Test
    fun whenRequestShouldAddGcpHeadersThenRedirectTriggeredByGpcCalled() = runTest {
        configureShouldNotUpgrade()
        configureShouldAddGpcHeader()
        configureUrlDoesNotExistInTheStack()
        val mockWebViewClientListener: WebViewClientListener = mock()

        testee.shouldIntercept(
            request = mockRequest,
            documentUrl = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener
        )

        verify(mockWebViewClientListener).redirectTriggeredByGpc()
    }

    @Test
    fun whenRequestShouldAddGcpHeadersThenLoadUrlWithGpcHeaders() = runTest {
        configureShouldNotUpgrade()
        configureShouldAddGpcHeader()
        configureUrlDoesNotExistInTheStack()
        val mockWebViewClientListener: WebViewClientListener = mock()

        testee.shouldIntercept(
            request = mockRequest,
            documentUrl = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener
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
            documentUrl = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener
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
            documentUrl = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener
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
            documentUrl = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener
        )

        verify(webView, never()).loadUrl(any(), any())
    }

    @Test
    fun whenUserAgentShouldChangeThenReloadUrl() = runTest {
        configureUserAgentShouldChange()
        configureUrlDoesNotExistInTheStack()

        val mockWebViewClientListener: WebViewClientListener = mock()
        testee.shouldIntercept(
            request = mockRequest,
            documentUrl = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener
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
            documentUrl = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener
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
            documentUrl = null,
            webView = webView,
            webViewClientListener = mockWebViewClientListener
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
            documentUrl = "foo.com"
        )

        assertCancelledResponse(response)
    }

    @Test
    fun whenInterceptFromServiceWorkerAndRequestShouldBlockButThereIsASurrogateThenResponseReturnedContainsTheSurrogateData() = runTest {
        val availableSurrogate = SurrogateResponse(
            scriptId = "testId",
            responseAvailable = true,
            mimeType = "application/javascript",
            jsFunction = "javascript replacement function goes here"
        )
        whenever(mockResourceSurrogates.get(any())).thenReturn(availableSurrogate)

        configureShouldNotUpgrade()
        configureShouldBlock()
        val response = testee.shouldInterceptFromServiceWorker(
            request = mockRequest,
            documentUrl = "foo.com"
        )

        assertEquals(availableSurrogate.jsFunction.byteInputStream().read(), response!!.data.read())
    }

    @Test
    fun whenInterceptFromServiceWorkerAndRequestIsNullThenReturnNull() = runTest {
        assertNull(testee.shouldInterceptFromServiceWorker(request = null, documentUrl = "foo.com"))
    }

    @Test
    fun whenInterceptFromServiceWorkerAndDocumentUrlIsNullThenReturnNull() = runTest {
        assertNull(testee.shouldInterceptFromServiceWorker(request = mockRequest, documentUrl = null))
    }

    private fun assertRequestCanContinueToLoad(response: WebResourceResponse?) {
        assertNull(response)
    }

    private fun configureShouldBlock() {
        val blockTrackingEvent = TrackingEvent(
            blocked = true,
            documentUrl = "",
            trackerUrl = "",
            entity = null,
            categories = null,
            surrogateId = "testId"
        )
        whenever(mockRequest.isForMainFrame).thenReturn(false)
        whenever(mockTrackerDetector.evaluate(any(), any())).thenReturn(blockTrackingEvent)
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
    }

    private fun configureShouldUpgrade() = runTest {
        whenever(mockHttpsUpgrader.shouldUpgrade(any())).thenReturn(true)
        whenever(mockHttpsUpgrader.upgrade(any())).thenReturn(validHttpsUri())
        whenever(mockRequest.url).thenReturn(validUri())
        whenever(mockRequest.isForMainFrame).thenReturn(true)
    }

    private fun configureShouldNotUpgrade() = runTest {
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

    private fun configureUserAgent() {
        val settings: WebSettings = mock()
        whenever(webView.settings).thenReturn(settings)
        whenever(settings.userAgentString).thenReturn(userAgentProvider.userAgent())
    }

    companion object {
        const val DEFAULT =
            "Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/64.0.3282.137 Mobile Safari/537.36"
    }
}
