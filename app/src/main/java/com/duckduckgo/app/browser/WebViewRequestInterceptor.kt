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
import com.duckduckgo.app.analyticsSurrogates.AnalyticsSurrogates
import com.duckduckgo.app.global.isHttp
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.privacymonitor.model.TrustedSites
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.model.ResourceType
import timber.log.Timber
import javax.inject.Inject


class WebViewRequestInterceptor @Inject constructor(
        private val analyticsSurrogates: AnalyticsSurrogates,
        private val trackerDetector: TrackerDetector,
        private val httpsUpgrader: HttpsUpgrader
) {

    fun shouldIntercept(
            request: WebResourceRequest,
            webView: WebView,
            currentUrl: String?,
            webViewClientListener: WebViewClientListener?
    ): WebResourceResponse? {
        val url = request.url

        if (shouldUpgrade(request)) {
            val newUri = httpsUpgrader.upgrade(url)
            webView.post { webView.loadUrl(newUri.toString()) }
            return WebResourceResponse(null, null, null)
        }

        val documentUrl = currentUrl ?: return null

        if (TrustedSites.isTrusted(documentUrl)) {
            return null
        }

        if (url != null && url.isHttp) {
            webViewClientListener?.pageHasHttpResources(documentUrl)
        }

        if (shouldBlock(request, documentUrl, webViewClientListener)) {

            val surrogate = analyticsSurrogates.get(url)
            if (surrogate.responseAvailable) {
                Timber.i("Surrogate found for %s", url)
                return WebResourceResponse(
                        surrogate.mimeType,
                        "UTF-8",
                        surrogate.jsFunction.byteInputStream()
                )
            }

            return WebResourceResponse(null, null, null)
        }

        return null
    }

    private fun shouldUpgrade(request: WebResourceRequest) =
            request.isForMainFrame && request.url != null && httpsUpgrader.shouldUpgrade(request.url)

    private fun shouldBlock(
            request: WebResourceRequest,
            documentUrl: String?,
            webViewClientListener: WebViewClientListener?
    ): Boolean {
        val url = request.url.toString()

        if (request.isForMainFrame || documentUrl == null) {
            return false
        }

        val trackingEvent =
                trackerDetector.evaluate(url, documentUrl, ResourceType.from(request))
                        ?: return false
        webViewClientListener?.trackerDetected(trackingEvent)
        return trackingEvent.blocked
    }

}