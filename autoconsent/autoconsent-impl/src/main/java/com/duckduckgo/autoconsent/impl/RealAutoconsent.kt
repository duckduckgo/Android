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

package com.duckduckgo.autoconsent.impl

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import timber.log.Timber
import java.io.BufferedReader
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealAutoconsent @Inject constructor() : Autoconsent {

    private lateinit var functions: String

    override fun injectAutoconsent(webView: WebView) {
        Timber.d("MARCOS injecting autoconsent")
        webView.evaluateJavascript("javascript:${getFunctionsJS()}", null)
    }

    override fun addJsInterface(webView: WebView) {
        webView.addJavascriptInterface(
            AutoconsentInterface(),
            "MARCOS"
        )
    }

    private fun getFunctionsJS(): String {
        if (!this::functions.isInitialized) {
            functions = loadJs("autoconsent-bundle.js")
        }
        return functions
    }

    private fun loadJs(resourceName: String): String = readResource(resourceName).use { it?.readText() }.orEmpty()

    private fun readResource(resourceName: String): BufferedReader? {
        return javaClass.classLoader?.getResource(resourceName)?.openStream()?.bufferedReader()
    }
}

class AutoconsentInterface {
    @JavascriptInterface
    fun console(message: String) {
        Timber.d("MARCOS message is $message")
    }
}
