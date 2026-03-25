/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.bridge.api

import android.webkit.WebView
import androidx.annotation.MainThread

/**
 * Manages the lifecycle of the duck.ai JS bridge on a WebView.
 *
 * Call [attachToWebView] at `onPageStarted` for duck.ai WebViews — this registers all bridge
 * handlers before JS runs. Call [detachFromWebView] when the WebView is no longer used for
 * duck.ai content.
 */
interface DuckAiBridgeManager {
    @MainThread
    fun attachToWebView(webView: WebView)

    @MainThread
    fun detachFromWebView(webView: WebView)
}
