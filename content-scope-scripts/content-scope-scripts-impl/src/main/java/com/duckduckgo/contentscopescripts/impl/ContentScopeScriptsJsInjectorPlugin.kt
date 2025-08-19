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

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

@ContributesMultibinding(AppScope::class)
class ContentScopeScriptsJsInjectorPlugin @Inject constructor(
    private val coreContentScopeScripts: CoreContentScopeScripts,
    private val adsJsContentScopeScripts: AdsJsContentScopeScripts,
    private val contentScopeExperiments: ContentScopeExperiments,
    private val dispatcherProvider: DispatcherProvider,
    private val webViewCompatWrapper: WebViewCompatWrapper,
) : JsInjectorPlugin {
    private var script: ScriptHandler? = null
    private var currentScriptString: String? = null

    private var activeExperiments: List<Toggle> = emptyList()

    @SuppressLint("RequiresFeature")
    private suspend fun reloadJSIfNeeded(
        webView: WebView,
    ) {
        activeExperiments = withContext(dispatcherProvider.io()) { contentScopeExperiments.getActiveExperiments() }

        withContext(dispatcherProvider.main()) {
            if (!webViewCompatWrapper.isDocumentStartScriptSupported()) {
                return@withContext
            }
            val scriptString = adsJsContentScopeScripts.getScript(activeExperiments)
            if (scriptString == currentScriptString) {
                return@withContext
            }
            script?.let {
                it.remove()
                script = null
            }
            if (coreContentScopeScripts.isEnabled()) {
                currentScriptString = scriptString
                script = webViewCompatWrapper.addDocumentStartJavaScript(webView, scriptString, setOf("*"))
            }
        }
    }

    override suspend fun onInit(
        webView: WebView,
    ) {
        reloadJSIfNeeded(webView)
    }

    override suspend fun onPageStarted(
        webView: WebView,
        url: String?,
        isDesktopMode: Boolean?,
    ): List<Toggle> {
        if (coreContentScopeScripts.isEnabled()) {
            webView.evaluateJavascript("javascript:${coreContentScopeScripts.getScript(isDesktopMode, activeExperiments)}", null)
            return activeExperiments
        }
        return listOf()
    }

    override suspend fun onPageFinished(
        webView: WebView,
        url: String?,
    ) {
        reloadJSIfNeeded(webView)
    }
}
