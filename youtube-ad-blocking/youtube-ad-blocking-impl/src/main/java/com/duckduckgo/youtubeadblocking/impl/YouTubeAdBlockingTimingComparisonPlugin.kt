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

import android.webkit.WebView
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import javax.inject.Inject

/**
 * Timing comparison: injects the probe script via evaluateJavascript in onPageStarted
 * (Mechanism C) alongside the shouldInterceptRequest HTML modification (Mechanism B).
 *
 * This does NOT inject the full ad-blocking scriptlets — only the lightweight timing
 * probe, tagged as [DDG-YT-ADBLOCK-EVALUATE] so it's distinguishable from the
 * HTML-injected [DDG-YT-ADBLOCK] probe in logcat.
 *
 * Compare the two probe outputs in logcat to determine if evaluateJavascript timing
 * is fast enough (ytInitialData: false = before YouTube init).
 *
 * If evaluateJavascript is fast enough, it would be the preferred approach because:
 * - No HTML modification needed
 * - No CSP stripping needed
 * - No OkHttp fetch / cookie bridging needed
 * - evaluateJavascript is not subject to CSP
 */
@ContributesMultibinding(AppScope::class)
class YouTubeAdBlockingTimingComparisonPlugin @Inject constructor(
    private val youTubeAdBlockingFeature: YouTubeAdBlockingFeature,
) : JsInjectorPlugin {

    override fun onPageStarted(
        webView: WebView,
        url: String?,
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ) {
        if (!youTubeAdBlockingFeature.self().isEnabled()) return
        if (url == null || !isYouTubeUrl(url)) return

        logcat { "YouTubeAdBlocking: evaluateJavascript timing comparison for $url" }

        webView.evaluateJavascript(TIMING_PROBE_SCRIPT, null)
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

    companion object {
        /**
         * Same probe as youtube_ad_blocking_probe.js but tagged differently so the
         * two injection mechanisms are distinguishable in logcat.
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
