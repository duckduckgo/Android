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
import androidx.annotation.WorkerThread
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.privacy.model.TrustedSites
import com.duckduckgo.app.surrogates.ResourceSurrogates
import com.duckduckgo.app.trackerdetection.CloakedCnameDetector
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.isHttp
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.httpsupgrade.api.HttpsUpgrader
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.request.filterer.api.RequestFilterer
import com.duckduckgo.user.agent.api.UserAgentProvider
import kotlinx.coroutines.withContext
import timber.log.Timber

interface RequestInterceptor {

    @WorkerThread
    suspend fun shouldIntercept(
        request: WebResourceRequest,
        webView: WebView,
        documentUri: Uri?,
        webViewClientListener: WebViewClientListener?,
    ): WebResourceResponse?

    @WorkerThread
    suspend fun shouldInterceptFromServiceWorker(
        request: WebResourceRequest?,
        documentUrl: Uri?,
    ): WebResourceResponse?

    fun onPageStarted(url: String)
}

class WebViewRequestInterceptor(
    private val resourceSurrogates: ResourceSurrogates,
    private val trackerDetector: TrackerDetector,
    private val httpsUpgrader: HttpsUpgrader,
    private val privacyProtectionCountDao: PrivacyProtectionCountDao,
    private val gpc: Gpc,
    private val userAgentProvider: UserAgentProvider,
    private val adClickManager: AdClickManager,
    private val cloakedCnameDetector: CloakedCnameDetector,
    private val requestFilterer: RequestFilterer,
    private val duckPlayer: DuckPlayer,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : RequestInterceptor {

    override fun onPageStarted(url: String) {
        requestFilterer.registerOnPageCreated(url)
    }

    /**
     * Notify the application of a resource request and allow the application to return the data.
     *
     * If the return value is null, the WebView will continue to load the resource as usual.
     * Otherwise, the return response and data will be used.
     *
     * NOTE: This method is called on a thread other than the UI thread so clients should exercise
     * caution when accessing private data or the view system.
     */
    @WorkerThread
    override suspend fun shouldIntercept(
        request: WebResourceRequest,
        webView: WebView,
        documentUri: Uri?,
        webViewClientListener: WebViewClientListener?,
    ): WebResourceResponse? {
        val url: Uri? = request.url

        if (requestFilterer.shouldFilterOutRequest(request, documentUri.toString())) return WebResourceResponse(null, null, null)

        adClickManager.detectAdClick(url?.toString(), request.isForMainFrame)

        newUserAgent(request, webView, webViewClientListener)?.let {
            withContext(dispatchers.main()) {
                webView.settings?.userAgentString = it
                webView.loadUrl(url.toString(), getHeaders(request))
            }
            return WebResourceResponse(null, null, null)
        }

        if (appUrlPixel(url)) return null

        if (shouldUpgrade(request)) {
            val newUri = url?.let { httpsUpgrader.upgrade(url) }

            withContext(dispatchers.main()) {
                webView.loadUrl(newUri.toString(), getHeaders(request))
            }

            webViewClientListener?.upgradedToHttps()
            privacyProtectionCountDao.incrementUpgradeCount()
            return WebResourceResponse(null, null, null)
        }

        if (url != null) {
            duckPlayer.intercept(request, url, webView)?.let { return it }
        }

        if (url != null && shouldAddGcpHeaders(request) && !requestWasInTheStack(url, webView)) {
            withContext(dispatchers.main()) {
                webViewClientListener?.redirectTriggeredByGpc()
                webView.loadUrl(url.toString(), getHeaders(request))
            }
            return WebResourceResponse(null, null, null)
        }

        if (documentUri == null) return null

        if (TrustedSites.isTrusted(documentUri)) {
            return null
        }

        if (url != null && url.isHttp) {
            webViewClientListener?.pageHasHttpResources(documentUri)
        }

        return getWebResourceResponse(request, documentUri, webViewClientListener)
    }

    override suspend fun shouldInterceptFromServiceWorker(
        request: WebResourceRequest?,
        documentUrl: Uri?,
    ): WebResourceResponse? {
        if (documentUrl == null) return null
        if (request == null) return null

        if (TrustedSites.isTrusted(documentUrl)) {
            return null
        }

        return getWebResourceResponse(request, documentUrl, null)
    }

    private fun getWebResourceResponse(
        request: WebResourceRequest,
        documentUrl: Uri,
        webViewClientListener: WebViewClientListener?,
    ): WebResourceResponse? {
        val trackingEvent = trackingEvent(request, documentUrl, webViewClientListener)
        if (trackingEvent?.status == TrackerStatus.BLOCKED) {
            return blockRequest(trackingEvent, request, webViewClientListener)
        } else if (trackingEvent == null ||
            trackingEvent.status == TrackerStatus.ALLOWED ||
            trackingEvent.status == TrackerStatus.SAME_ENTITY_ALLOWED
        ) {
            cloakedCnameDetector.detectCnameCloakedHost(documentUrl.toString(), request.url)?.let { uncloakedHost ->
                trackingEvent(request, documentUrl, webViewClientListener, false, uncloakedHost)?.let { cloakedTrackingEvent ->
                    if (cloakedTrackingEvent.status == TrackerStatus.BLOCKED) {
                        return blockRequest(cloakedTrackingEvent, request, webViewClientListener)
                    }
                }
            }
        }
        return null
    }

    private fun blockRequest(
        trackingEvent: TrackingEvent,
        request: WebResourceRequest,
        webViewClientListener: WebViewClientListener?,
    ): WebResourceResponse {
        trackingEvent.surrogateId?.let { surrogateId ->
            val surrogate = resourceSurrogates.get(surrogateId)
            if (surrogate.responseAvailable) {
                Timber.d("Surrogate found for ${request.url}")
                webViewClientListener?.surrogateDetected(surrogate)
                return WebResourceResponse(surrogate.mimeType, "UTF-8", surrogate.jsFunction.byteInputStream())
            }
        }

        Timber.d("Blocking request ${request.url}")
        privacyProtectionCountDao.incrementBlockedTrackerCount()
        return WebResourceResponse(null, null, null)
    }

    private fun getHeaders(request: WebResourceRequest): Map<String, String> {
        return request.requestHeaders.apply {
            putAll(gpc.getHeaders(request.url.toString()))
        }
    }

    private fun shouldAddGcpHeaders(request: WebResourceRequest): Boolean {
        val existingHeaders = request.requestHeaders
        return (request.isForMainFrame && request.method == "GET" && gpc.canUrlAddHeaders(request.url.toString(), existingHeaders))
    }

    private suspend fun requestWasInTheStack(
        url: Uri,
        webView: WebView,
    ): Boolean {
        return withContext(dispatchers.main()) {
            val webBackForwardList = webView.copyBackForwardList()
            webBackForwardList.currentItem?.url == url.toString()
        }
    }

    private suspend fun newUserAgent(
        request: WebResourceRequest,
        webView: WebView,
        webViewClientListener: WebViewClientListener?,
    ): String? {
        return if (request.isForMainFrame && request.method == "GET") {
            val url = request.url ?: return null
            if (requestWasInTheStack(url, webView)) return null
            val desktopSiteEnabled = webViewClientListener?.isDesktopSiteEnabled() == true
            val currentAgent = withContext(dispatchers.main()) { webView.settings?.userAgentString }
            val newAgent = userAgentProvider.userAgent(url.toString(), desktopSiteEnabled)
            return if (currentAgent != newAgent) {
                newAgent
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun shouldUpgrade(request: WebResourceRequest) =
        request.isForMainFrame && request.url != null && httpsUpgrader.shouldUpgrade(request.url)

    private fun trackingEvent(
        request: WebResourceRequest,
        documentUrl: Uri?,
        webViewClientListener: WebViewClientListener?,
        checkFirstParty: Boolean = true,
    ): TrackingEvent? {
        val url = request.url
        if (request.isForMainFrame || documentUrl == null) {
            return null
        }

        val trackingEvent = trackerDetector.evaluate(url, documentUrl, checkFirstParty, request.requestHeaders) ?: return null
        webViewClientListener?.trackerDetected(trackingEvent)
        return trackingEvent
    }

    private fun trackingEvent(
        request: WebResourceRequest,
        documentUrl: Uri?,
        webViewClientListener: WebViewClientListener?,
        checkFirstParty: Boolean = true,
        url: String = request.url.toString(),
    ): TrackingEvent? {
        if (request.isForMainFrame || documentUrl == null) {
            return null
        }

        val trackingEvent = trackerDetector.evaluate(url, documentUrl, checkFirstParty, request.requestHeaders) ?: return null
        webViewClientListener?.trackerDetected(trackingEvent)
        return trackingEvent
    }

    private fun appUrlPixel(url: Uri?): Boolean =
        url?.toString()?.startsWith(AppUrl.Url.PIXEL) == true
}
