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
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockerWebViewIntegration
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData.Ignored
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData.MaliciousSite
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData.Safe
import com.duckduckgo.app.browser.webview.RealMaliciousSiteBlockerWebViewIntegration.IsMaliciousViewData.WaitForConfirmation
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
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
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus.Malicious
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.request.filterer.api.RequestFilterer
import com.duckduckgo.user.agent.api.UserAgentProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

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

    @WorkerThread
    fun shouldOverrideUrlLoading(
        webViewClientListener: WebViewClientListener?,
        url: Uri,
        documentUrl: Uri?,
        isForMainFrame: Boolean,
    ): Boolean

    fun addExemptedMaliciousSite(url: Uri, feed: Feed)
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
    private val maliciousSiteBlockerWebViewIntegration: MaliciousSiteBlockerWebViewIntegration,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    @IsMainProcess private val isMainProcess: Boolean,
) : RequestInterceptor {

    private var checkMaliciousAfterHttpsUpgrade = false

    init {
        if (isMainProcess) {
            loadToMemory()
        }
    }

    private fun loadToMemory() {
        appCoroutineScope.launch(dispatchers.io()) {
            checkMaliciousAfterHttpsUpgrade = androidBrowserConfigFeature.checkMaliciousAfterHttpsUpgrade().isEnabled()
        }
    }

    override fun onPageStarted(url: String) {
        requestFilterer.registerOnPageCreated(url)
        maliciousSiteBlockerWebViewIntegration.onPageLoadStarted(url)
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
        val url: Uri = request.url

        if (!checkMaliciousAfterHttpsUpgrade) {
            maliciousSiteBlockerWebViewIntegration.shouldIntercept(request, documentUri) { isMalicious ->
                handleConfirmationCallback(isMalicious, webViewClientListener, url, documentUri, request.isForMainFrame)
            }.let {
                if (shouldBlock(it, webViewClientListener, url, documentUri, request.isForMainFrame)) return WebResourceResponse(null, null, null)
            }
        }

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

        if (checkMaliciousAfterHttpsUpgrade) {
            maliciousSiteBlockerWebViewIntegration.shouldIntercept(request, documentUri) { isMalicious ->
                handleConfirmationCallback(isMalicious, webViewClientListener, url, documentUri, request.isForMainFrame)
            }.let {
                if (shouldBlock(it, webViewClientListener, url, documentUri, request.isForMainFrame)) return WebResourceResponse(null, null, null)
            }
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

    override fun shouldOverrideUrlLoading(
        webViewClientListener: WebViewClientListener?,
        url: Uri,
        documentUrl: Uri?,
        isForMainFrame: Boolean,
    ): Boolean {
        maliciousSiteBlockerWebViewIntegration.shouldOverrideUrlLoading(
            url,
            isForMainFrame,
        ) { isMalicious ->
            handleConfirmationCallback(isMalicious, webViewClientListener, url, documentUrl, isForMainFrame)
        }.let {
            return shouldBlock(it, webViewClientListener, url, documentUrl, isForMainFrame)
        }
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

    private fun shouldBlock(
        result: IsMaliciousViewData,
        webViewClientListener: WebViewClientListener?,
        url: Uri,
        documentUrl: Uri?,
        isForMainFrame: Boolean,
    ): Boolean {
        when (result) {
            WaitForConfirmation, Ignored -> return false
            is Safe -> {
                handleSiteSafe(webViewClientListener = webViewClientListener, uri = url, isForMainFrame = result.isForMainFrame)
                return false
            }
            is MaliciousSite -> {
                handleSiteBlocked(
                    webViewClientListener = webViewClientListener,
                    maliciousUri = url,
                    documentUrl = documentUrl,
                    feed = result.feed,
                    exempted = result.exempted,
                    clientSideHit = result.clientSideHit,
                    isForMainFrame = isForMainFrame,
                )
                return !result.exempted
            }
        }
    }

    private fun handleConfirmationCallback(
        isMalicious: MaliciousStatus,
        webViewClientListener: WebViewClientListener?,
        url: Uri,
        documentUrl: Uri?,
        isForMainFrame: Boolean,
    ) {
        when (isMalicious) {
            is Malicious -> {
                /* If the site is exempted, we'll never get here, as we won't call isMalicious */
                handleSiteBlocked(
                    webViewClientListener = webViewClientListener,
                    maliciousUri = url,
                    documentUrl = documentUrl,
                    feed = isMalicious.feed,
                    exempted = false,
                    clientSideHit = false,
                    isForMainFrame = isForMainFrame,
                )
            }
            is MaliciousStatus.Safe -> {
                handleSiteSafe(webViewClientListener = webViewClientListener, uri = url, isForMainFrame = isForMainFrame)
            }
            is MaliciousStatus.Ignored -> { /* Do nothing */ }
        }
    }

    private fun handleSiteBlocked(
        webViewClientListener: WebViewClientListener?,
        maliciousUri: Uri,
        documentUrl: Uri?,
        feed: Feed,
        exempted: Boolean,
        clientSideHit: Boolean,
        isForMainFrame: Boolean,
    ) {
        webViewClientListener?.onReceivedMaliciousSiteWarning(
            url = if (isForMainFrame || documentUrl == null) maliciousUri else documentUrl,
            feed = feed,
            exempted = exempted,
            clientSideHit = clientSideHit,
            isMainframe = isForMainFrame,
        )
    }

    private fun handleSiteSafe(
        webViewClientListener: WebViewClientListener?,
        uri: Uri?,
        isForMainFrame: Boolean,
    ) {
        uri?.let { webViewClientListener?.onReceivedMaliciousSiteSafe(url = it, isForMainFrame = isForMainFrame) }
    }

    override fun addExemptedMaliciousSite(url: Uri, feed: Feed) {
        maliciousSiteBlockerWebViewIntegration.onSiteExempted(url, feed)
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
                logcat { "Surrogate found for ${request.url}" }
                webViewClientListener?.surrogateDetected(surrogate)
                return WebResourceResponse(surrogate.mimeType, "UTF-8", surrogate.jsFunction.byteInputStream())
            }
        }

        logcat { "Blocking request ${request.url}" }
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
