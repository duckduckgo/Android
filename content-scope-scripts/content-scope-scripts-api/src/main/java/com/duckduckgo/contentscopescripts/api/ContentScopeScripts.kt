/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.api

import android.webkit.WebView

/** Public interface for the Content Scope Scripts feature */
interface ContentScopeScripts {

    /**
     * This method injects the content scope scripts JS code into the [WebView].
     * It requires a [WebView] instance.
     */
    fun injectContentScopeScripts(webView: WebView)

    /**
     * This method adds the JS interface for Content Scope Scripts to create a bridge between JS and our client.
     * It requires a [WebView] instance.
     */
    fun addJsInterface(webView: WebView)

    /**
     * This method sends a message to Content Scope Scripts.
     * It requires a JSON message [String] and a [WebView] instance.
     */
    fun sendMessage(message: String, webView: WebView)
}
