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

package com.duckduckgo.app.accessibility.plaintextmetaviewport

import android.content.Context
import android.webkit.WebView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class PlainTextMetaViewportJsInjectorPlugin @Inject constructor(
    private val plainTextMetaViewportFeature: PlainTextMetaViewportFeature,
) : JsInjectorPlugin {
    private val javaScriptInjector = JavaScriptInjector()

    override fun onPageStarted(webView: WebView, url: String?, site: Site?) {
        if (plainTextMetaViewportFeature.self().isEnabled()) {
            webView.evaluateJavascript("javascript:${javaScriptInjector.getFunctionsJS(webView.context)}", null)
        }
    }

    override fun onPageFinished(webView: WebView, url: String?, site: Site?) {
        // No-op
    }

    private class JavaScriptInjector {
        private lateinit var functions: String

        fun getFunctionsJS(
            context: Context,
        ): String {
            if (!this::functions.isInitialized) {
                functions = context.resources.openRawResource(R.raw.plain_text_meta_viewport).bufferedReader().use { it.readText() }
            }
            return functions
        }
    }
}
