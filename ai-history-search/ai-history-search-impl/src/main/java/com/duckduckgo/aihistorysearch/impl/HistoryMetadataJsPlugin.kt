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

@ContributesMultibinding(AppScope::class)
class HistoryMetadataJsPlugin @Inject constructor(
    private val feature: AiHistorySearchFeature,
    private val navigationHistory: NavigationHistory,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : JsInjectorPlugin {

    private val adapter = Moshi.Builder().build()
        .adapter<Map<String, String?>>(Types.newParameterizedType(Map::class.java, String::class.java, String::class.java))

    override fun onPageStarted(webView: WebView, url: String?, isDesktopMode: Boolean?, activeExperiments: List<Toggle>) {
        // no-op
    }

    override fun onPageFinished(webView: WebView, url: String?, site: Site?) {
        if (!feature.historyMetadataEnabled().isEnabled()) return
        val pageUrl = url ?: return

        val js = """
            (function() {
                var d = document.querySelector('meta[property="og:description"]')?.content
                    || document.querySelector('meta[name="description"]')?.content
                    || null;
                var h = document.querySelector('h1')?.innerText?.trim() || null;
                return JSON.stringify({description: d, h1: h});
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            if (result == null || result == "null") return@evaluateJavascript
            try {
                // evaluateJavascript wraps the returned JS string in quotes and escapes internals.
                val unquoted = result.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                val map = adapter.fromJson(unquoted) ?: return@evaluateJavascript
                val description = map["description"]?.takeIf { it.isNotBlank() }
                val h1 = map["h1"]?.takeIf { it.isNotBlank() }
                if (description != null || h1 != null) {
                    logcat { "HistoryMetadata: url=$pageUrl description=${description?.take(80)} h1=${h1?.take(60)}" }
                    appScope.launch { navigationHistory.updateHistoryMetadata(pageUrl, description, h1) }
                }
            } catch (e: Exception) {
                logcat { "HistoryMetadata: parse error — ${e.message}" }
            }
        }
    }
}
