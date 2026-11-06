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

package com.duckduckgo.app.browser.progressbar

import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.webkit.Navigation
import androidx.webkit.NavigationListener
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * WebViewClient exposes no HTTP status for a successful main-frame response, the only signal for some challenge pages.
 */
class MainFrameCommitNavigationClient(
    private val onMainFrameCommitted: (statusCode: Int) -> Unit,
) : NavigationListener {

    override fun onNavigationCompleted(navigation: Navigation) {
        if (navigation.didCommit() && !navigation.isSameDocument && !navigation.didCommitErrorPage()) {
            onMainFrameCommitted(navigation.statusCode)
        }
    }

    companion object {
        fun install(
            webView: WebView,
            onMainFrameCommitted: (statusCode: Int) -> Unit,
        ) {
            // Support depends on the installed WebView, not the API level. unsupported WebViews fall back to no challenge detection.
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.NAVIGATION_LISTENER)) return
            WebViewCompat.addNavigationListener(
                webView,
                ContextCompat.getMainExecutor(webView.context),
                MainFrameCommitNavigationClient(onMainFrameCommitted),
            )
        }
    }
}
