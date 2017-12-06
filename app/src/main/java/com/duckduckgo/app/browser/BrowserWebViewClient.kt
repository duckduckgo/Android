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
import android.support.annotation.AnyThread
import android.support.annotation.WorkerThread
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.duckduckgo.app.global.isHttp
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.model.ResourceType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import timber.log.Timber
import javax.inject.Inject


class BrowserWebViewClient @Inject constructor(
        private val requestRewriter: DuckDuckGoRequestRewriter,
        private var trackerDetector: TrackerDetector
) : WebViewClient() {

    var webViewClientListener: WebViewClientListener? = null
    private var currentUrl: String? = null


    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (requestRewriter.shouldRewriteRequest(request)) {
            val newUri = requestRewriter.rewriteRequestWithCustomQueryParams(request.url)
            view.loadUrl(newUri.toString())
            return true
        }
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        currentUrl = url
        webViewClientListener?.loadingStarted()
        webViewClientListener?.urlChanged(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        webViewClientListener?.loadingFinished()
    }

    @WorkerThread
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        Timber.v("Intercepting resource ${request.url}")

        if (request.url != null && request.url.isHttp) {
            webViewClientListener?.pageHasHttpResources()
        }

        if (view.elementClicked() == request.url.toString()) {
            return null
        }

        if (block(request, currentUrl)) {
            return WebResourceResponse(null, null, null)
        }

        return null
    }

    private fun block(request: WebResourceRequest, documentUrl: String?): Boolean {
        val url = request.url.toString()

        if (documentUrl != null && trackerDetector.shouldBlock(url, documentUrl, ResourceType.from(request))) {
            webViewClientListener?.trackerDetected(TrackingEvent(url, documentUrl, true))
            return true
        }

        return false
    }

    private fun WebView.elementClicked(): String? = safeHitTestResult().extra

    /**
     * Access the webview hit test result from any thread; jumps onto the main thread to achieve this
     */
    @AnyThread
    private fun WebView.safeHitTestResult(): WebView.HitTestResult {
        return Observable.just(this)
                .observeOn(AndroidSchedulers.mainThread())
                .map { webView ->
                    webView.hitTestResult
                }
                .blockingFirst()
    }
}
