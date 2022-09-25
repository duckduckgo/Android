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

import android.webkit.JavascriptInterface
import timber.log.Timber

class UrlExtractionJavascriptInterface(private val onUrlExtracted: (extractedUrl: String?) -> Unit) {

    @JavascriptInterface
    fun urlExtracted(extractedUrl: String?) {
        onUrlExtracted(extractedUrl)
    }

    @JavascriptInterface
    fun log(message: String?) {
        Timber.d("AMP link detection: $message")
    }

    companion object {
        // Interface name used inside url_extraction.js
        const val URL_EXTRACTION_JAVASCRIPT_INTERFACE_NAME = "UrlExtraction"
    }
}
