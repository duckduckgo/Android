/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import com.duckduckgo.autofill.impl.configuration.AutofillJavascriptLoader
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutofillJavascriptInjector {
    suspend fun addDocumentStartJavascript(webView: WebView)
}

@ContributesBinding(FragmentScope::class)
class AutofillJavascriptInjectorImpl @Inject constructor(
    private val javascriptLoader: AutofillJavascriptLoader,
) : AutofillJavascriptInjector {

    @SuppressLint("RequiresFeature")
    override suspend fun addDocumentStartJavascript(webView: WebView) {
        val js = javascriptLoader.getAutofillJavascript()
            .replace("// INJECT userPreferences HERE", staticJavascript)

        WebViewCompat.addDocumentStartJavaScript(webView, js, setOf("*"))
    }

    companion object {
        private val staticJavascript = """
            userPreferences = {
                "debug": false,
                "platform": {
                    "name": "android"
                }
            }
        """.trimIndent()
    }
}
