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

import android.app.Application
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
 * Page metadata extracted from a visited page.
 *
 * @property description The page meta description (og:description or meta[name=description]), if available.
 * @property h1 The primary heading (first h1 element) of the page, if available.
 * @property chunkText The readable body text extracted by Readability.js, capped at 2000 chars, if available.
 */
internal data class PageMetadata(
    val description: String?,
    val h1: String?,
    val chunkText: String?,
)

/**
 * Parses page metadata from a JSON result produced by [evaluateJavascript].
 * The JS returns a plain object `{description, h1, chunkText}` so the result is already
 * a JSON object string — no unquoting needed.
 *
 * Returns [PageMetadata] when at least one value is non-blank, null otherwise.
 */
internal fun parseMetadata(result: String): PageMetadata? {
    return try {
        val map = HistoryMetadataJsPlugin.ADAPTER.fromJson(result) ?: return null
        val description = map["description"]?.takeIf { it.isNotBlank() }
        val h1 = map["h1"]?.takeIf { it.isNotBlank() }
        val chunkText = map["chunkText"]?.takeIf { it.isNotBlank() }
        if (description == null && h1 == null && chunkText == null) null
        else PageMetadata(description, h1, chunkText)
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
    private val application: Application,
) : JsInjectorPlugin {

    private val readabilityJs: String by lazy {
        application.assets.open("readability.js").bufferedReader().use { it.readText() }
    }

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

        // Readability.js source is prepended so it is available in the same evaluation context.
        // The extractor returns a plain object — evaluateJavascript delivers it as a JSON string
        // directly to Moshi without any manual unquoting.
        val js = buildString {
            append(readabilityJs)
            append("""
                ;(function() {
                    var d = document.querySelector('meta[property="og:description"]')?.content
                        || document.querySelector('meta[name="description"]')?.content
                        || null;
                    var h = document.querySelector('h1')?.textContent?.trim() || null;
                    var chunkText = null;
                    try {
                        var docClone = document.cloneNode(true);
                        var article = new Readability(docClone).parse();
                        if (article && article.textContent) {
                            chunkText = article.textContent.replace(/\s+/g, ' ').trim().substring(0, 2000) || null;
                        }
                    } catch(e) {}
                    return {description: d, h1: h, chunkText: chunkText};
                })()
            """.trimIndent())
        }

        webView.evaluateJavascript(js) { result ->
            if (result == null) return@evaluateJavascript
            val metadata = parseMetadata(result) ?: return@evaluateJavascript
            logcat {
                "HistoryMetadata: url=$pageUrl description=${metadata.description?.take(80)} " +
                    "h1=${metadata.h1?.take(60)} chunkText=${metadata.chunkText?.take(60)}"
            }
            appScope.launch {
                navigationHistory.updateHistoryMetadata(pageUrl, metadata.description, metadata.h1, metadata.chunkText)
            }
        }
    }
}
