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

package com.duckduckgo.app.browser.api

import android.webkit.WebView
import androidx.webkit.WebViewCompat.WebMessageListener

/**
 * Add and remove web message listeners to a WebView, guarded by extra checks to ensure WebView compatibility
 */
interface SafeWebMessageHandler {

    suspend fun addWebMessageListener(
        webView: WebView,
        jsObjectName: String,
        allowedOriginRules: Set<String>,
        listener: WebMessageListener,
    ): Boolean

    suspend fun removeWebMessageListener(
        webView: WebView,
        jsObjectName: String,
    ): Boolean
}
