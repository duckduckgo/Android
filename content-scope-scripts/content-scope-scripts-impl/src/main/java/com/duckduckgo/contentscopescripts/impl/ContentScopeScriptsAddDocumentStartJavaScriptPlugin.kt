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
import androidx.webkit.ScriptHandler
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

@ContributesMultibinding(AppScope::class)
class ContentScopeScriptsAddDocumentStartJavaScriptPlugin @Inject constructor(
    private val webViewCompatContentScopeScripts: WebViewCompatContentScopeScripts,
    private val dispatcherProvider: DispatcherProvider,
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
) : AddDocumentStartJavaScriptPlugin {
    private var script: ScriptHandler? = null
    private var currentScriptString: String? = null

    @SuppressLint("RequiresFeature")
    override suspend fun configureAddDocumentStartJavaScript(
        activeExperiments: List<Toggle>,
        scriptInjector: suspend (scriptString: String, allowedOriginRules: Set<String>) -> ScriptHandler?,
    ) {
        if (!webViewCompatContentScopeScripts.isEnabled() || !webViewCapabilityChecker.isSupported(
                WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript,
            )
        ) {
            return
        }

        val scriptString = webViewCompatContentScopeScripts.getScript(activeExperiments)
        if (scriptString == currentScriptString) {
            return
        }
        withContext(dispatcherProvider.main()) {
            script?.let {
                it.remove()
                script = null
            }

            scriptInjector(scriptString, setOf("*"))?.let {
                script = it
                currentScriptString = scriptString
            }
        }
    }
}
