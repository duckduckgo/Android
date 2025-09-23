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

package com.duckduckgo.js.messaging.impl

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptPlugin
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptScriptStrategy
import com.duckduckgo.js.messaging.api.AddDocumentStartScriptDelegate
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
/**
 * Delegate class that implements AddDocumentStartJavaScriptPlugin and handles
 * the common script injection logic
 */
class RealAddDocumentStartScriptDelegate @Inject constructor(
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val webViewCompatWrapper: WebViewCompatWrapper,
) : AddDocumentStartScriptDelegate {

    override fun createPlugin(strategy: AddDocumentStartJavaScriptScriptStrategy): AddDocumentStartJavaScriptPlugin {
        return object : AddDocumentStartJavaScriptPlugin {

            private var script: ScriptHandler? = null
            private var currentScriptString: String? = null

            @SuppressLint("RequiresFeature")
            override suspend fun addDocumentStartJavaScript(webView: WebView) {
                if (!strategy.canInject() || !webViewCapabilityChecker.isSupported(
                        WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript,
                    )
                ) {
                    return
                }

                val scriptString = strategy.getScriptString()
                if (scriptString == currentScriptString) {
                    return
                }
                script?.let {
                    withContext(dispatcherProvider.main()) {
                        it.remove()
                    }
                    script = null
                }

                webViewCompatWrapper.addDocumentStartJavaScript(
                    webView,
                    scriptString,
                    strategy.allowedOriginRules,
                )?.let {
                    script = it
                    currentScriptString = scriptString
                }
            }

            override val context: String
                get() = strategy.context
        }
    }
}
