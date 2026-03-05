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

package com.duckduckgo.aihistorysearch.impl

import android.webkit.WebView
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.history.api.NavigationHistory
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

/**
 * Parses page metadata (og:description / meta description and h1) from a result string produced
 * by [evaluateJavascript] when the JS returns an object directly (not JSON.stringify'd).
 *
 * Returns a (description, h1) pair when at least one value is non-blank, null otherwise.
 */
internal fun parseMetadata(result: String): Pair<String?, String?>? {
    return try {
        val map = HistoryMetadataJsPlugin.ADAPTER.fromJson(result) ?: return null
        val description = map["description"]?.takeIf { it.isNotBlank() }
        val h1 = map["h1"]?.takeIf { it.isNotBlank() }
        if (description == null && h1 == null) null else Pair(description, h1)
    } catch (e: Exception) {
        logcat(tag = "HistoryMetadataJsPlugin") { "HistoryMetadata: parse error — ${e.message}" }
        null
    }
}

@ContributesMultibinding(AppScope::class)
class HistoryMetadataJsPlugin @Inject constructor(
    private val feature: AiHistorySearchFeature,
    private val navigationHistory: NavigationHistory,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : JsInjectorPlugin {

    companion object {
        val ADAPTER = Moshi.Builder().build()
            .adapter<Map<String, String?>>(Types.newParameterizedType(Map::class.java, String::class.java, String::class.java))
    }

    override fun onPageStarted(webView: WebView, url: String?, isDesktopMode: Boolean?, activeExperiments: List<Toggle>) {
        // no-op
    }

    override fun onPageFinished(webView: WebView, url: String?, site: Site?) {
        if (!feature.historyMetadataEnabled().isEnabled()) return
        val pageUrl = url ?: return

        // Return the object directly (not JSON.stringify) so that evaluateJavascript gives us
        // a plain JSON object string rather than a double-encoded quoted string.
        val js = """
            (function() {
                var d = document.querySelector('meta[property="og:description"]')?.content
                    || document.querySelector('meta[name="description"]')?.content
                    || null;
                var h = document.querySelector('h1')?.textContent?.trim() || null;
                return {description: d, h1: h};
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            if (result == null) return@evaluateJavascript
            val (description, h1) = parseMetadata(result) ?: return@evaluateJavascript
            logcat { "HistoryMetadata: url=$pageUrl description=${description?.take(80)} h1=${h1?.take(60)}" }
            appScope.launch { navigationHistory.updateHistoryMetadata(pageUrl, description, h1) }
        }
    }
}
