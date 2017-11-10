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

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.duckduckgo.app.trackerdetection.TrackerDetector
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_main.view.*
import timber.log.Timber


class BrowserWebViewClient(val trackerDetector: TrackerDetector) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        Timber.v("Intercepting Url ${request?.url}")

        if (block(request?.url?.toString(), view?.url)) {
            return true
        }

        return false
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        Timber.v("Intercepting resource ${request?.url}")

        if (block(request?.url?.toString(), view?.safeUrl())) {
            return WebResourceResponse(null, null, null)
        }

        return null
    }

    private fun block(url: String?, documentUrl: String?): Boolean {

        if (url != null && documentUrl != null && trackerDetector.shouldBlock(url, documentUrl)) {
            Timber.v("BLOCKED ${url}")
            return true
        }

        Timber.v("NOT blocked ${url}")
        return false
    }

    fun WebView.safeUrl(): String? {
        return Observable.just(webView)
                .observeOn(AndroidSchedulers.mainThread())
                .map { webView -> webView.url }
                .blockingFirst()
    }

}
