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
        val timingEnabled = settingsStore.timingEvaluate

        logcat { "YouTubeAdBlocking [evaluate plugin] onPageStarted $url | ${settingsStore.settingsSummary()}" }

        if (method == InjectMethod.EVALUATE) {
            logcat { "YouTubeAdBlocking [evaluate plugin] INJECTING SCRIPTLETS via evaluateJavascript (timing=$timingEnabled)" }
            val bundle = getFullBundle(includeProbe = timingEnabled)
            if (bundle != null) {
                webView.evaluateJavascript(bundle, null)
            }
        } else if (timingEnabled) {
            logcat { "YouTubeAdBlocking [evaluate plugin] TIMING PROBE ONLY (active injection method: $method)" }
            webView.evaluateJavascript(TIMING_PROBE_SCRIPT, null)
        } else {
            logcat { "YouTubeAdBlocking [evaluate plugin] SKIPPED — not active method and timing disabled" }
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

    private fun getFullBundle(includeProbe: Boolean): String? {
        val scriptlets = cachedFullBundle ?: try {
            val main = loadRawResource(R.raw.youtube_ad_blocking_main)
            val isolated = loadRawResource(R.raw.youtube_ad_blocking_isolated)
            "$main\n$isolated".also { cachedFullBundle = it }
        } catch (e: Exception) {
            logcat(ERROR) { "YouTubeAdBlocking: Failed to load scriptlet bundle: ${e.message}" }
            return null
        }
        return if (includeProbe) {
            "$scriptlets\n$TIMING_PROBE_SCRIPT"
        } else {
            scriptlets
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
