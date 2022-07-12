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
import androidx.annotation.UiThread
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.Dispatchers
import java.io.BufferedReader
import javax.inject.Inject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesBinding(AppScope::class)
class RealAutoconsent @Inject constructor() : Autoconsent {

    private lateinit var functions: String
    private lateinit var rules: String

    override fun injectAutoconsent(webView: WebView) {
        Timber.d("MARCOS injecting autoconsent")
        webView.evaluateJavascript("javascript:${getFunctionsJS()}", null)
    }

    override fun addJsInterface(webView: WebView) {
        webView.addJavascriptInterface(AutoconsentInterface(this, webView), "MARCOS")
    }

    @UiThread
    override fun init(webView: WebView) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                Timber.d("MARCOS receiving init and responding")
                webView.evaluateJavascript("javascript:${selftTest()}", null)
            } catch (e: Exception) {
                Timber.d("MARCOS exception is ${e.localizedMessage}")
            }
        }
        // webView.evaluateJavascript("javascript:${initResp()}", null)
    }

    private fun selftTest(): String {
        return """
            (function() {
                window.autoconsentMessageCallback({type: 'selfTest'}, window.origin);
            })();
        """.trimIndent()
    }

    private fun initResp(): String {
        return """
            (function() {
                window.postMessage({type: 'initResp', rules: '${getRules()}', config: '{'enabled': true}'}, window.origin);
            })();
        """.trimIndent()
    }

    private fun getFunctionsJS(): String {
        if (!this::functions.isInitialized) {
            functions = loadJs("autoconsent-bundle.js")
        }
        return functions
    }

    private fun getRules(): String {
        if (!this::rules.isInitialized) {
            rules = loadJs("rules.json")
        }
        return rules
    }

    private fun loadJs(resourceName: String): String =
        readResource(resourceName).use { it?.readText() }.orEmpty()

    private fun readResource(resourceName: String): BufferedReader? {
        return javaClass.classLoader?.getResource(resourceName)?.openStream()?.bufferedReader()
    }
}

class AutoconsentInterface(val autoconsent: RealAutoconsent, val webView: WebView) {
    @JavascriptInterface
    fun console(message: String) {
        Timber.d("MARCOS message is $message")
        autoconsent.init(webView)
    }
}
