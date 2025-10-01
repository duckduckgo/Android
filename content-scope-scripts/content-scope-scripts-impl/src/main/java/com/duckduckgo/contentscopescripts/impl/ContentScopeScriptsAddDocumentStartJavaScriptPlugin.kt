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
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SingleInstanceIn(FragmentScope::class)
@ContributesMultibinding(FragmentScope::class)
class ContentScopeScriptsAddDocumentStartJavaScriptPlugin @Inject constructor(
    private val webViewCompatContentScopeScripts: WebViewCompatContentScopeScripts,
    private val dispatcherProvider: DispatcherProvider,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
    private val webViewCompatWrapper: WebViewCompatWrapper,
    private val contentScopeExperiments: ContentScopeExperiments,
) : AddDocumentStartJavaScriptPlugin {
    private var script: ScriptHandler? = null
    private var currentScriptString: String? = null

    @SuppressLint("RequiresFeature")
    override suspend fun addDocumentStartJavaScript(webView: WebView) {
        if (!webViewCompatContentScopeScripts.isEnabled() ||
            !webViewCapabilityChecker.isSupported(
                WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript,
            )
        ) {
            return
        }

        val activeExperiments = contentScopeExperiments.getActiveExperiments()
        val scriptString = webViewCompatContentScopeScripts.getScript(activeExperiments)
        if (scriptString == currentScriptString) {
            return
        }
        script?.let {
            withContext(dispatcherProvider.main()) {
                it.remove()
            }
            script = null
        }

        webViewCompatWrapper
            .addDocumentStartJavaScript(
                webView,
                scriptString,
                setOf("*"),
            )?.let {
                script = it
                currentScriptString = scriptString
            }
    }
}
