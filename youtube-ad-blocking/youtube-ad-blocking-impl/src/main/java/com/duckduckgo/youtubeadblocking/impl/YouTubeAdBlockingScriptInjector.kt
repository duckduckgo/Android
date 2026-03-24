/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.youtubeadblocking.impl

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptPlugin
import com.duckduckgo.youtubeadblocking.api.YouTubeAdBlocking
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

/**
 * Injects YouTube ad-blocking scriptlets at document start via [WebViewCompat.addDocumentStartJavaScript].
 *
 * Key properties:
 * - Executes before any page JavaScript (document_start equivalent)
 * - Automatically covers all frames including iframes
 * - Persists across SPA navigations (pushState)
 * - Not subject to the page's CSP
 * - Scoped to youtube.com and m.youtube.com only
 *
 * For the hack phase, injects a timing probe script. In production this will be
 * replaced with the actual scriptlet bundle from content-blocker-extension.
 */
@SingleInstanceIn(FragmentScope::class)
@ContributesMultibinding(FragmentScope::class)
class YouTubeAdBlockingScriptInjector @Inject constructor(
    private val youTubeAdBlocking: YouTubeAdBlocking,
    private val dispatcherProvider: DispatcherProvider,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
    private val webViewCompatWrapper: WebViewCompatWrapper,
) : AddDocumentStartJavaScriptPlugin {

    private var scriptHandler: ScriptHandler? = null

    @SuppressLint("RequiresFeature")
    override suspend fun addDocumentStartJavaScript(webView: WebView) {
        if (!youTubeAdBlocking.isEnabled() ||
            !webViewCapabilityChecker.isSupported(DocumentStartJavaScript)
        ) {
            return
        }

        // Only register once per WebView lifecycle
        if (scriptHandler != null) return

        val script = withContext(dispatcherProvider.io()) {
            loadScript(webView.context)
        }

        logcat { "YouTubeAdBlocking: Registering document start script for YouTube origins" }

        webViewCompatWrapper.addDocumentStartJavaScript(
            webView,
            script,
            YOUTUBE_ORIGIN_RULES,
        )?.let {
            scriptHandler = it
        }
    }

    override val context: String = "youTubeAdBlocking"

    private fun loadScript(context: android.content.Context): String {
        return context.resources.openRawResource(R.raw.youtube_ad_blocking_probe)
            .bufferedReader()
            .use { it.readText() }
    }

    companion object {
        /**
         * Origin rules restricting script injection to YouTube domains only.
         * addDocumentStartJavaScript will only inject the script when the page origin
         * matches one of these rules.
         */
        val YOUTUBE_ORIGIN_RULES = setOf(
            "https://www.youtube.com",
            "https://m.youtube.com",
            "https://youtube.com",
        )
    }
}
