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

import android.content.Context
import android.webkit.WebView
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/**
 * Injects YouTube ad-blocking scriptlets via evaluateJavascript in onPageStarted.
 *
 * Controlled by the `useEvaluateJs` sub-feature toggle:
 * - When `useEvaluateJs` is OFF: injects only a lightweight timing probe tagged
 *   [DDG-YT-ADBLOCK-EVALUATE] for comparison with the HTML injection approach.
 * - When `useEvaluateJs` is ON: injects the full scriptlet bundle (main + isolated + probe).
 *   The shouldInterceptRequest interceptor stands down in this mode.
 *
 * evaluateJavascript advantages over HTML modification:
 * - No CSP stripping needed (evaluateJavascript is not subject to page CSP)
 * - No OkHttp fetch / cookie bridging / redirect handling needed
 * - Simpler, more stable
 *
 * evaluateJavascript disadvantage:
 * - Fires at onPageStarted which may be slightly later than document_start
 */
@ContributesMultibinding(AppScope::class)
class YouTubeAdBlockingEvaluateJsPlugin @Inject constructor(
    private val context: Context,
    private val youTubeAdBlockingFeature: YouTubeAdBlockingFeature,
) : JsInjectorPlugin {

    private var cachedFullBundle: String? = null

    override fun onPageStarted(
        webView: WebView,
        url: String?,
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ) {
        if (!youTubeAdBlockingFeature.self().isEnabled()) return
        if (url == null || !isYouTubeUrl(url)) return

        val useEvaluateJs = youTubeAdBlockingFeature.useEvaluateJs().isEnabled()

        if (useEvaluateJs) {
            // Full injection mode: inject the complete scriptlet bundle
            val bundle = getFullBundle()
            if (bundle != null) {
                logcat { "YouTubeAdBlocking: [evaluateJs mode] Injecting full scriptlet bundle for $url" }
                webView.evaluateJavascript(bundle, null)
            }
        } else {
            // Comparison mode: inject only the timing probe
            logcat { "YouTubeAdBlocking: [timing comparison] evaluateJavascript probe for $url" }
            webView.evaluateJavascript(TIMING_PROBE_SCRIPT, null)
        }
    }

    override fun onPageFinished(
        webView: WebView,
        url: String?,
        site: Site?,
    ) {
        // no-op
    }

    private fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com/", ignoreCase = true)
    }

    private fun getFullBundle(): String? {
        cachedFullBundle?.let { return it }
        return try {
            val main = loadRawResource(R.raw.youtube_ad_blocking_main)
            val isolated = loadRawResource(R.raw.youtube_ad_blocking_isolated)
            val probe = loadRawResource(R.raw.youtube_ad_blocking_probe)
            "$main\n$isolated\n$probe".also { cachedFullBundle = it }
        } catch (e: Exception) {
            logcat(ERROR) { "YouTubeAdBlocking: Failed to load scriptlet bundle: ${e.message}" }
            null
        }
    }

    private fun loadRawResource(resId: Int): String {
        return context.resources.openRawResource(resId)
            .bufferedReader()
            .use { it.readText() }
    }

    companion object {
        /**
         * Lightweight timing probe tagged differently from the HTML-injected probe
         * so the two mechanisms are distinguishable in logcat.
         */
        private const val TIMING_PROBE_SCRIPT = """
(function() {
    var TAG = '[DDG-YT-ADBLOCK-EVALUATE]';
    var timing = performance.now();
    var ytDataDefined = typeof window.ytInitialData !== 'undefined';
    var ytcfgDefined = typeof window.ytcfg !== 'undefined';
    var ytPlayerDefined = typeof window.ytInitialPlayerResponse !== 'undefined';
    var isMainFrame = window === window.top;
    console.log(
        TAG,
        'Injected at', timing.toFixed(2), 'ms',
        '| ytInitialData:', ytDataDefined,
        '| ytcfg:', ytcfgDefined,
        '| ytPlayerResponse:', ytPlayerDefined,
        '| frame:', isMainFrame ? 'main' : 'iframe',
        '| url:', location.href
    );
})();
"""
    }
}
