/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.browser.print

import android.webkit.WebView
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker
import com.duckduckgo.app.browser.api.WebViewCapabilityChecker.WebViewCapability.DocumentStartJavaScript
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.webviewcompat.WebViewCompatWrapper
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface PrintInjector {
    fun addJsInterface(
        webView: WebView,
        onPrintDetected: () -> Unit,
    )

    fun injectPrint(
        webView: WebView,
    )
}

@ContributesBinding(AppScope::class)
class PrintInjectorJS @Inject constructor(
    private val webViewCapabilityChecker: WebViewCapabilityChecker,
    private val webViewCompatWrapper: WebViewCompatWrapper,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : PrintInjector {
    override fun addJsInterface(
        webView: WebView,
        onPrintDetected: () -> Unit,
    ) {
        webView.addJavascriptInterface(PrintJavascriptInterface(onPrintDetected), PrintJavascriptInterface.JAVASCRIPT_INTERFACE_NAME)

        // Override window.print in every frame (including cross-origin iframes) at document start so that
        // calls made from within an iframe are routed to the native print flow. WebView ignores window.print()
        // invoked inside an iframe, so the main-frame-only override in injectPrint() is not enough.
        // See https://issues.chromium.org/issues/40342444
        appCoroutineScope.launch(dispatcherProvider.main()) {
            if (webViewCapabilityChecker.isSupported(DocumentStartJavaScript)) {
                webViewCompatWrapper.addDocumentStartJavaScript(webView, PRINT_OVERRIDE_SCRIPT, ALLOWED_ORIGIN_RULES)
            }
        }
    }

    override fun injectPrint(webView: WebView) {
        // Main-frame override, kept for WebViews that don't support document start scripts. On WebViews that do
        // support them the override has already been registered for all frames in addJsInterface().
        webView.loadUrl("javascript:$PRINT_OVERRIDE_SCRIPT")
    }

    companion object {
        private val ALLOWED_ORIGIN_RULES = setOf("*")
        private val PRINT_OVERRIDE_SCRIPT =
            "window.print = function() { ${PrintJavascriptInterface.JAVASCRIPT_INTERFACE_NAME}.print() }"
    }
}
