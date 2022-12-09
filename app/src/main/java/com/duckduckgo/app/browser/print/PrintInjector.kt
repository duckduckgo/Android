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
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
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
class PrintInjectorJS @Inject constructor() : PrintInjector {
    override fun addJsInterface(
        webView: WebView,
        onPrintDetected: () -> Unit,
    ) {
        webView.addJavascriptInterface(PrintJavascriptInterface(onPrintDetected), PrintJavascriptInterface.JAVASCRIPT_INTERFACE_NAME)
    }

    override fun injectPrint(webView: WebView) {
        webView.loadUrl("javascript:window.print = function() { ${PrintJavascriptInterface.JAVASCRIPT_INTERFACE_NAME}.print() }")
    }
}
