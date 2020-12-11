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

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.isHttp
import com.duckduckgo.app.globalprivacycontrol.GlobalPrivacyControl
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.privacy.model.TrustedSites
import com.duckduckgo.app.surrogates.ResourceSurrogates
import com.duckduckgo.app.trackerdetection.TrackerDetector
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
}

class WebViewRequestInterceptor(
    private val resourceSurrogates: ResourceSurrogates,
    private val trackerDetector: TrackerDetector,
    private val httpsUpgrader: HttpsUpgrader,
    private val privacyProtectionCountDao: PrivacyProtectionCountDao,
    private val globalPrivacyControl: GlobalPrivacyControl
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

        if (shouldUpgrade(request)) {
            val newUri = httpsUpgrader.upgrade(url)

            withContext(Dispatchers.Main) {
                val headers = request.requestHeaders
                headers.putAll(globalPrivacyControl.getHeaders())
                webView.loadUrl(newUri.toString(), headers)
            }

            webViewClientListener?.upgradedToHttps()
            privacyProtectionCountDao.incrementUpgradeCount()
            return WebResourceResponse(null, null, null)
        }

        val headers = request.requestHeaders
        if (globalPrivacyControl.isGpcActive() && request.isForMainFrame && request.hasGesture() && request.method == "GET" && !headers.containsKey("sec-gpc")) {

            headers.putAll(globalPrivacyControl.getHeaders())

            Timber.d("MARCOS add headers ${documentUrl} ${request.hasGesture()} ${request.method} ${request.url}")
            headers.keys.map {
                Timber.d("MARCOS key $it value ${headers[it]}")
            }
            withContext(Dispatchers.Main) {
                webViewClientListener?.redirectTriggeredByGpc()
                webView.loadUrl(url.toString(), headers)
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

        if (shouldBlock(request, documentUrl, webViewClientListener)) {
            val surrogate = resourceSurrogates.get(url)
            if (surrogate.responseAvailable) {
                Timber.d("Surrogate found for $url")
                webViewClientListener?.surrogateDetected(surrogate)
                return WebResourceResponse(surrogate.mimeType, "UTF-8", surrogate.jsFunction.byteInputStream())
            }

            Timber.d("Blocking request $url")
            privacyProtectionCountDao.incrementBlockedTrackerCount()
            return WebResourceResponse(null, null, null)
        }

        return null
    }

    private fun shouldUpgrade(request: WebResourceRequest) =
        request.isForMainFrame && request.url != null && httpsUpgrader.shouldUpgrade(request.url)

    private fun shouldBlock(request: WebResourceRequest, documentUrl: String?, webViewClientListener: WebViewClientListener?): Boolean {
        val url = request.url.toString()

        if (request.isForMainFrame || documentUrl == null) {
            return false
        }

        val trackingEvent = trackerDetector.evaluate(url, documentUrl) ?: return false
        webViewClientListener?.trackerDetected(trackingEvent)
        return trackingEvent.blocked
    }

}
