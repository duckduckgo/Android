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
import com.duckduckgo.app.browser.useragent.UserAgentProvider
import com.duckduckgo.app.global.isHttp
import com.duckduckgo.app.globalprivacycontrol.GlobalPrivacyControl
import com.duckduckgo.app.globalprivacycontrol.GlobalPrivacyControlManager
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.privacy.model.TrustedSites
import com.duckduckgo.app.surrogates.ResourceSurrogates
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

interface RequestInterceptor {

    @WorkerThread
    suspend fun shouldIntercept(
        request: WebResourceRequest,
        webView: WebView,
        documentUrl: String?,
        webViewClientListener: WebViewClientListener?
    ): WebResourceResponse?

    @WorkerThread
    suspend fun shouldInterceptFromServiceWorker(
        request: WebResourceRequest?,
        documentUrl: String?
    ): WebResourceResponse?
}

class WebViewRequestInterceptor(
    private val resourceSurrogates: ResourceSurrogates,
    private val trackerDetector: TrackerDetector,
    private val httpsUpgrader: HttpsUpgrader,
    private val privacyProtectionCountDao: PrivacyProtectionCountDao,
    private val globalPrivacyControl: GlobalPrivacyControl,
    private val userAgentProvider: UserAgentProvider
) : RequestInterceptor {

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
        documentUrl: String?,
        webViewClientListener: WebViewClientListener?
    ): WebResourceResponse? {

        val url = request.url

        newUserAgent(request, webView, webViewClientListener)?.let {
            withContext(Dispatchers.Main) {
                webView.settings?.userAgentString = it
                webView.loadUrl(url.toString(), getHeaders(request))
            }
            return WebResourceResponse(null, null, null)
        }

        if (shouldUpgrade(request)) {
            val newUri = httpsUpgrader.upgrade(url)

            withContext(Dispatchers.Main) {
                webView.loadUrl(newUri.toString(), getHeaders(request))
            }

            webViewClientListener?.upgradedToHttps()
            privacyProtectionCountDao.incrementUpgradeCount()
            return WebResourceResponse(null, null, null)
        }

        if (shouldAddGcpHeaders(request) && !requestWasInTheStack(url, webView)) {
            withContext(Dispatchers.Main) {
                webViewClientListener?.redirectTriggeredByGpc()
                webView.loadUrl(url.toString(), getHeaders(request))
            }
            return WebResourceResponse(null, null, null)
        }

        if (documentUrl == null) return null

        if (TrustedSites.isTrusted(documentUrl)) {
            return null
        }

        if (url != null && url.isHttp) {
            webViewClientListener?.pageHasHttpResources(documentUrl)
        }

        return getWebResourceResponse(request, documentUrl, webViewClientListener)
    }

    override suspend fun shouldInterceptFromServiceWorker(
        request: WebResourceRequest?,
        documentUrl: String?
    ): WebResourceResponse? {

        if (documentUrl == null) return null
        if (request == null) return null

        if (TrustedSites.isTrusted(documentUrl)) {
            return null
        }

        return getWebResourceResponse(request, documentUrl, null)
    }

    private fun getWebResourceResponse(request: WebResourceRequest, documentUrl: String?, webViewClientListener: WebViewClientListener?): WebResourceResponse? {
        val trackingEvent = trackingEvent(request, documentUrl, webViewClientListener)
        if (trackingEvent?.blocked == true) {
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

        return null
    }

    private fun getHeaders(request: WebResourceRequest): Map<String, String> {
        return request.requestHeaders.apply {
            putAll(globalPrivacyControl.getHeaders(request.url.toString()))
        }
    }

    private fun shouldAddGcpHeaders(request: WebResourceRequest): Boolean {
        val headers = request.requestHeaders
        return (
            globalPrivacyControl.canPerformARedirect(request.url) &&
                !headers.containsKey(GlobalPrivacyControlManager.GPC_HEADER) &&
                request.isForMainFrame &&
                request.method == "GET"
            )
    }

    private suspend fun requestWasInTheStack(url: Uri, webView: WebView): Boolean {
        return withContext(Dispatchers.Main) {
            val webBackForwardList = webView.copyBackForwardList()
            webBackForwardList.currentItem?.url == url.toString()
        }
    }

    private suspend fun newUserAgent(
        request: WebResourceRequest,
        webView: WebView,
        webViewClientListener: WebViewClientListener?
    ): String? {
        return if (request.isForMainFrame && request.method == "GET") {
            val url = request.url ?: return null
            if (requestWasInTheStack(url, webView)) return null
            val desktopSiteEnabled = webViewClientListener?.isDesktopSiteEnabled() == true
            val currentAgent = withContext(Dispatchers.Main) { webView.settings?.userAgentString }
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

    private fun trackingEvent(request: WebResourceRequest, documentUrl: String?, webViewClientListener: WebViewClientListener?): TrackingEvent? {
        val url = request.url.toString()

        if (request.isForMainFrame || documentUrl == null) {
            return null
        }

        val trackingEvent = trackerDetector.evaluate(url, documentUrl) ?: return null
        webViewClientListener?.trackerDetected(trackingEvent)
        return trackingEvent
    }
}
