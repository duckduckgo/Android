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
import com.duckduckgo.app.browser.WebViewRequestInterceptor.InterceptAction
import com.duckduckgo.app.global.isHttp
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.privacy.model.TrustedSites
import com.duckduckgo.app.surrogates.ResourceSurrogates
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.model.ResourceType
import timber.log.Timber

interface RequestInterceptor {

    @WorkerThread
    suspend fun shouldIntercept(
        request: WebResourceRequest,
        webView: WebView,
        documentUrl: String?,
        webViewClientListener: WebViewClientListener?
    ): InterceptAction
}

class WebViewRequestInterceptor(
    private val resourceSurrogates: ResourceSurrogates,
    private val trackerDetector: TrackerDetector,
    private val httpsUpgrader: HttpsUpgrader,
    private val privacyProtectionCountDao: PrivacyProtectionCountDao

) : RequestInterceptor {

    /**
     * Notify the application of a resource request and allow the application to return the data.
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
    ): InterceptAction {

        val url = request.url

        if (shouldUpgrade(request)) {
            privacyProtectionCountDao.incrementUpgradeCount()
            return InterceptAction.BlockAndLoadAlternativeUrl(httpsUpgrader.upgrade(url))
        }

        if (documentUrl == null) return InterceptAction.Allow

        if (TrustedSites.isTrusted(documentUrl)) {
            return InterceptAction.Allow
        }

        if (url != null && url.isHttp) {
            webViewClientListener?.pageHasHttpResources(documentUrl)
        }

        if (shouldBlock(request, documentUrl, webViewClientListener)) {
            val surrogate = resourceSurrogates.get(url)
            if (surrogate.responseAvailable) {
                Timber.d("Surrogate found for $url")
                val surrgoateResponse = WebResourceResponse(surrogate.mimeType, "UTF-8", surrogate.jsFunction.byteInputStream())
                return InterceptAction.BlockAndSubstituteSurrogate(surrgoateResponse)
            }

            Timber.d("Blocking request $url")
            privacyProtectionCountDao.incrementBlockedTrackerCount()
            return InterceptAction.Block
        }

        return InterceptAction.Allow
    }

    private fun shouldUpgrade(request: WebResourceRequest) =
        request.isForMainFrame && request.url != null && httpsUpgrader.shouldUpgrade(request.url)

    private fun shouldBlock(request: WebResourceRequest, documentUrl: String?, webViewClientListener: WebViewClientListener?): Boolean {
        val url = request.url.toString()

        if (request.isForMainFrame || documentUrl == null) {
            return false
        }

        val trackingEvent = trackerDetector.evaluate(url, documentUrl, ResourceType.from(request)) ?: return false
        webViewClientListener?.trackerDetected(trackingEvent)
        return trackingEvent.blocked
    }

    sealed class InterceptAction {
        object Allow : InterceptAction()
        object Block : InterceptAction()
        data class BlockAndLoadAlternativeUrl(val url: Uri) : InterceptAction()
        data class BlockAndSubstituteSurrogate(val webResourceResponse: WebResourceResponse) : InterceptAction()
    }
}