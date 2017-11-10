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
import android.webkit.WebView
import android.webkit.WebViewClient
import timber.log.Timber
import javax.inject.Inject

class BrowserWebViewClient @Inject constructor(private val requestRewriter: DuckDuckGoRequestRewriter): WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        Timber.v("Url ${request.url}")

        if (requestRewriter.shouldRewriteRequest(request)) {
            val newUri = requestRewriter.rewriteRequestWithCustomQueryParams(request.url)
            view.loadUrl(newUri.toString())
            return true
        }

        return false
    }
}
