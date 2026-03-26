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

package com.duckduckgo.scriptlet.impl

import android.webkit.WebView
import androidx.annotation.UiThread
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import logcat.logcat

@ContributesMultibinding(AppScope::class)
class ScriptletJsInjectorPlugin @Inject constructor(
    private val scriptletFeature: ScriptletFeature,
) : JsInjectorPlugin {

    private val mainWorldScript: String by lazy {
        ScriptletJsReader.loadJs("main-ublock-filters.js")
    }

    private val isolatedWorldScript: String by lazy {
        ScriptletJsReader.loadJs("isolated-ublock-filters.js")
    }

    @UiThread
    override fun onPageStarted(
        webView: WebView,
        url: String?,
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ) {
        if (!scriptletFeature.self().isEnabled()) return
        if (url == null || !isYouTubeUrl(url)) return

        if (mainWorldScript.isNotEmpty()) {
            webView.evaluateJavascript(mainWorldScript, null)
            logcat { "Scriptlet: injected main world script on $url" }
        }
        if (isolatedWorldScript.isNotEmpty()) {
            webView.evaluateJavascript(isolatedWorldScript, null)
            logcat { "Scriptlet: injected isolated world script on $url" }
        }
    }

    override fun onPageFinished(webView: WebView, url: String?, site: Site?) {
    }

    private fun isYouTubeUrl(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return false
        return host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtu.be"
    }
}
