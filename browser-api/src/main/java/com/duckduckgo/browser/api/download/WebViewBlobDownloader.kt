/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.browser.api.download

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy

/**
 * This interface provides the ability to add modern blob download support to a WebView.
 */
interface WebViewBlobDownloader {

    /**
     * Configures a web view to support blob downloads, including in iframes.
     */
    suspend fun addBlobDownloadSupport(webView: WebView)

    /**
     * Requests the WebView to convert a blob URL to a data URI.
     */
    suspend fun convertBlobToDataUri(blobUrl: String)

    /**
     * Stores a reply proxy for a given location.
     */
    suspend fun storeReplyProxy(originUrl: String, replyProxy: JavaScriptReplyProxy, locationHref: String?)

    /**
     * Clears any stored JavaScript reply proxies.
     */
    fun clearReplyProxies()
}
