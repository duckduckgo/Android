/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.urlextraction

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.user.agent.api.UserAgentProvider

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class UrlExtractingWebView(
    context: Context,
    webViewClient: UrlExtractingWebViewClient,
    userAgentProvider: UserAgentProvider,
    urlExtractor: DOMUrlExtractor,
) : WebView(context) {

    var urlExtractionListener: UrlExtractionListener? = null
    lateinit var initialUrl: String

    init {
        settings.apply {
            userAgentString = userAgentProvider.userAgent()
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            loadsImagesAutomatically = false
        }
        setWebViewClient(webViewClient)

        if (BuildConfig.DEBUG) {
            setWebContentsDebuggingEnabled(true)
        }

        urlExtractor.addUrlExtraction(this) { extractedUrl ->
            urlExtractionListener?.onUrlExtracted(initialUrl, extractedUrl)
        }
    }

    override fun loadUrl(url: String) {
        initialUrl = url
        super.loadUrl(url)
    }

    fun destroyWebView() {
        stopLoading()
        clearHistory()
        loadUrl("about:blank")
        onPause()
        removeAllViews()
        destroy()
    }
}
