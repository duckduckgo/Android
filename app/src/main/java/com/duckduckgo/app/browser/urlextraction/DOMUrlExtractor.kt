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

import android.content.Context
import android.webkit.WebView
import androidx.annotation.UiThread
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.urlextraction.UrlExtractionJavascriptInterface.Companion.URL_EXTRACTION_JAVASCRIPT_INTERFACE_NAME

interface DOMUrlExtractor {
    fun addUrlExtraction(webView: WebView, onUrlExtracted: (extractedUrl: String?) -> Unit)
    fun injectUrlExtractionJS(webView: WebView)
}

class JsUrlExtractor : DOMUrlExtractor {
    private val javaScriptDetector = JavaScriptDetector()

    override fun addUrlExtraction(webView: WebView, onUrlExtracted: (extractedUrl: String?) -> Unit) {
        webView.addJavascriptInterface(UrlExtractionJavascriptInterface(onUrlExtracted), URL_EXTRACTION_JAVASCRIPT_INTERFACE_NAME)
    }

    @UiThread
    override fun injectUrlExtractionJS(webView: WebView) {
        webView.evaluateJavascript("javascript:${javaScriptDetector.urlExtractionEventsDetector(webView.context)}", null)
    }

    private class JavaScriptDetector {
        private lateinit var handlers: String

        fun urlExtractionEventsDetector(context: Context): String {
            return wrapIntoAnonymousFunction(getUrlExtractionJS(context))
        }

        private fun getUrlExtractionJS(context: Context): String {
            if (!this::handlers.isInitialized) {
                handlers = context.resources.openRawResource(R.raw.url_extraction).bufferedReader().use { it.readText() }
            }
            return handlers
        }

        private fun wrapIntoAnonymousFunction(rawJavaScript: String): String {
            return "(function() { $rawJavaScript })();"
        }
    }
}
