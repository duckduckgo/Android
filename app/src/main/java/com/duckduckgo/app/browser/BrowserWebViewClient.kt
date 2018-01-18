/*
 * Copyright (c) 2017 DuckDuckGo
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

import android.graphics.Bitmap
import android.net.Uri
import android.support.annotation.AnyThread
import android.support.annotation.WorkerThread
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.duckduckgo.app.global.isHttp
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.privacymonitor.model.TrustedSites
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.model.ResourceType
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import javax.inject.Inject


class BrowserWebViewClient @Inject constructor(
        private val requestRewriter: DuckDuckGoRequestRewriter,
        private var trackerDetector: TrackerDetector,
        private var httpsUpgrader: HttpsUpgrader,
        private val specialUrlDetector: SpecialUrlDetector
) : WebViewClient() {

    var webViewClientListener: WebViewClientListener? = null


    /**
     * This is the new method of url overriding available from API 24 onwards
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        return shouldOverride(view, url)
    }

    /**
     * * This is the old, deprecated method of url overriding available until API 23
     */
    @Suppress("OverridingDeprecatedMember")
    override fun shouldOverrideUrlLoading(view: WebView, urlString: String): Boolean {
        val url = Uri.parse(urlString)
        return shouldOverride(view, url)
    }

    /**
     * API-agnostic implementation of deciding whether to override url or not
     */
    private fun shouldOverride(view: WebView, url: Uri): Boolean {

        val urlType = specialUrlDetector.determineType(url)

        return when (urlType) {
            is SpecialUrlDetector.UrlType.Email -> consume { webViewClientListener?.sendEmailRequested(urlType.emailAddress) }
            is SpecialUrlDetector.UrlType.Telephone -> consume { webViewClientListener?.dialTelephoneNumberRequested(urlType.telephoneNumber) }
            is SpecialUrlDetector.UrlType.Sms -> consume { webViewClientListener?.sendSmsRequested(urlType.telephoneNumber) }
            is SpecialUrlDetector.UrlType.Web -> {
                if (requestRewriter.shouldRewriteRequest(url)) {
                    val newUri = requestRewriter.rewriteRequestWithCustomQueryParams(url)
                    view.loadUrl(newUri.toString())
                    return true
                }
                return false
            }
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        webViewClientListener?.loadingStarted()
        webViewClientListener?.urlChanged(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        webViewClientListener?.loadingFinished()
    }

    @WorkerThread
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        Timber.v("Intercepting resource ${request.url} on page ${view.urlFromWorkerThread()}}")

        if (shouldUpgrade(request)) {
            val newUri = httpsUpgrader.upgrade(request.url)
            view.post { view.loadUrl(newUri.toString()) }
            return WebResourceResponse(null, null, null)
        }

        val documentUrl = view.urlFromWorkerThread() ?: return null

        if (TrustedSites.isTrusted(documentUrl)) {
            return null
        }

        if (request.url != null && request.url.isHttp) {
            webViewClientListener?.pageHasHttpResources(documentUrl)
        }

        if (shouldBlock(request, documentUrl)) {
            return WebResourceResponse(null, null, null)
        }

        return null
    }

    private fun shouldUpgrade(request: WebResourceRequest) =
            request.isForMainFrame && request.url != null && httpsUpgrader.shouldUpgrade(request.url)

    private fun shouldBlock(request: WebResourceRequest, documentUrl: String?): Boolean {
        val url = request.url.toString()

        if (request.isForMainFrame || documentUrl == null) {
            return false
        }

        val trackingEvent = trackerDetector.evaluate(url, documentUrl, ResourceType.from(request)) ?: return false
        webViewClientListener?.trackerDetected(trackingEvent)
        return trackingEvent.blocked
    }

    /**
     * Utility to function to execute a function, and then return true
     *
     * Useful to reduce clutter in repeatedly including `return true` after doing the real work.
     */
    private inline fun consume(function: () -> Unit): Boolean {
        function()
        return true
    }

    @AnyThread
    private fun WebView.urlFromWorkerThread(): String? {
        val latch = CountDownLatch(1)
        var safeUrl: String? = null
        post {
            safeUrl = url
            latch.countDown()
        }
        latch.await()
        return safeUrl
    }

}
