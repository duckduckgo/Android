/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ContentScopeScriptsJsInjectorPlugin @Inject constructor(
    private val coreContentScopeScripts: CoreContentScopeScripts,
    private val adsJsContentScopeScripts: AdsJsContentScopeScripts,
) : JsInjectorPlugin {
    private var script: ScriptHandler? = null
    private var currentScriptString: String? = null

    // TODO: Remove params
    private fun reloadJSIfNeeded(
        webView: WebView,
        activeExperiments: List<Toggle>,
    ) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return
        }
        val scriptString = adsJsContentScopeScripts.getScript(activeExperiments)
        if (scriptString == currentScriptString) {
            return
        }
        script?.let {
            it.remove()
            script = null
        }
        if (coreContentScopeScripts.isEnabled()) {
            currentScriptString = scriptString
            script = WebViewCompat.addDocumentStartJavaScript(webView, scriptString, setOf("*"))
        }
    }

    override fun onInit(
        webView: WebView,
        activeExperiments: List<Toggle>,
    ) {
        reloadJSIfNeeded(webView, activeExperiments)
    }

    override fun onPageStarted(
        webView: WebView,
        url: String?,
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ) {
        if (coreContentScopeScripts.isEnabled()) {
            webView.evaluateJavascript("javascript:${coreContentScopeScripts.getScript(isDesktopMode, activeExperiments)}", null)
        }
    }

    override fun onPageFinished(
        webView: WebView,
        url: String?,
        activeExperiments: List<Toggle>,
    ) {
        // TODO: Check
        reloadJSIfNeeded(webView, activeExperiments)
    }
}
