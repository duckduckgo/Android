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
 * Active when `injectMethod` setting is `"evaluate"`.
 *
 * When injectMethod is anything else, only a lightweight timing probe is injected
 * (tagged [DDG-YT-ADBLOCK-EVALUATE]) for comparison with other mechanisms.
 */
@ContributesMultibinding(AppScope::class)
class YouTubeAdBlockingEvaluateJsPlugin @Inject constructor(
    private val context: Context,
    private val youTubeAdBlockingFeature: YouTubeAdBlockingFeature,
    private val settingsStore: YouTubeAdBlockingSettingsStore,
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

        val method = settingsStore.injectMethod

        if (method == InjectMethod.EVALUATE) {
            // Full injection mode: inject the complete scriptlet bundle
            val bundle = getFullBundle()
            if (bundle != null) {
                logcat { "YouTubeAdBlocking: [evaluate mode] Injecting full scriptlet bundle for $url" }
                webView.evaluateJavascript(bundle, null)
            }
        } else {
            // Timing comparison mode: inject only the lightweight probe
            logcat { "YouTubeAdBlocking: [timing comparison] evaluateJavascript probe for $url (active method: $method)" }
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
