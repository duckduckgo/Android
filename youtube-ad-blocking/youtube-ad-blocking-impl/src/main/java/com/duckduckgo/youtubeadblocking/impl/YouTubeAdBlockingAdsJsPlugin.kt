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
import android.content.Context
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/**
 * Injects YouTube ad-blocking scriptlets via addDocumentStartJavaScript (Mechanism A).
 *
 * Active when `injectMethod` setting is `"adsjs"`.
 *
 * Advantages:
 * - Executes before any page JavaScript (document_start equivalent)
 * - Automatically covers all frames including iframes
 * - Persists across SPA navigations (pushState)
 * - Not subject to the page's CSP
 *
 * Disadvantages:
 * - May crash on some WebView versions
 * - Requires WebView capability check
 */
@SingleInstanceIn(FragmentScope::class)
@ContributesMultibinding(FragmentScope::class)
class YouTubeAdBlockingAdsJsPlugin @Inject constructor(
    private val appContext: Context,
    private val youTubeAdBlockingFeature: YouTubeAdBlockingFeature,
    private val settingsStore: YouTubeAdBlockingSettingsStore,
    private val dispatcherProvider: DispatcherProvider,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
    private val webViewCompatWrapper: WebViewCompatWrapper,
) : AddDocumentStartJavaScriptPlugin {

    private var scriptHandler: ScriptHandler? = null
    private var cachedScriptlets: String? = null
    private var cachedProbe: String? = null

    @SuppressLint("RequiresFeature")
    override suspend fun addDocumentStartJavaScript(webView: WebView) {
        if (!youTubeAdBlockingFeature.self().isEnabled()) return
        if (!webViewCapabilityChecker.isSupported(DocumentStartJavaScript)) return

        val method = settingsStore.injectMethod
        val timingEnabled = settingsStore.timingAdsjs

        logcat { "YouTubeAdBlocking [adsjs plugin] addDocumentStartJavaScript called | ${settingsStore.settingsSummary()}" }

        // Only register if adsjs is the active method OR timing probe is enabled
        if (method != InjectMethod.ADSJS && !timingEnabled) {
            logcat { "YouTubeAdBlocking [adsjs plugin] SKIPPED — not active method and timing disabled" }
            return
        }

        // Only register once per WebView lifecycle
        if (scriptHandler != null) {
            logcat { "YouTubeAdBlocking [adsjs plugin] SKIPPED — already registered for this WebView" }
            return
        }

        val script = withContext(dispatcherProvider.io()) {
            if (method == InjectMethod.ADSJS) {
                getScriptBundle(includeProbe = timingEnabled)
            } else {
                getTimingProbe()
            }
        } ?: return

        if (method == InjectMethod.ADSJS) {
            logcat { "YouTubeAdBlocking [adsjs plugin] INJECTING SCRIPTLETS via addDocumentStartJavaScript (timing=$timingEnabled)" }
        } else {
            logcat { "YouTubeAdBlocking [adsjs plugin] TIMING PROBE ONLY (active injection method: $method)" }
        }

        webViewCompatWrapper.addDocumentStartJavaScript(
            webView,
            script,
            YOUTUBE_ORIGIN_RULES,
        )?.let {
            scriptHandler = it
        }
    }

    override val context: String = "youTubeAdBlocking"

    private fun getScriptBundle(includeProbe: Boolean): String? {
        return try {
            val scriptlets = cachedScriptlets ?: run {
                val main = loadRawResource(R.raw.youtube_ad_blocking_main)
                val isolated = loadRawResource(R.raw.youtube_ad_blocking_isolated)
                "$main\n$isolated".also { cachedScriptlets = it }
            }
            if (includeProbe) {
                val probe = cachedProbe ?: loadRawResource(R.raw.youtube_ad_blocking_probe).also { cachedProbe = it }
                "$scriptlets\n$probe"
            } else {
                scriptlets
            }
        } catch (e: Exception) {
            logcat(ERROR) { "YouTubeAdBlocking: Failed to load scriptlet bundle: ${e.message}" }
            null
        }
    }

    private fun getTimingProbe(): String {
        return ADSJS_TIMING_PROBE
    }

    private fun loadRawResource(resId: Int): String {
        return appContext.resources.openRawResource(resId)
            .bufferedReader()
            .use { it.readText() }
    }

    companion object {
        val YOUTUBE_ORIGIN_RULES = setOf(
            "https://www.youtube.com",
            "https://m.youtube.com",
            "https://youtube.com",
        )

        private const val ADSJS_TIMING_PROBE = """
(function() {
    var TAG = '[DDG-YT-ADBLOCK-ADSJS]';
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
