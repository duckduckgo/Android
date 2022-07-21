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

import android.webkit.WebView
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import timber.log.Timber

@ContributesBinding(AppScope::class)
class RealAutoconsent @Inject constructor(
    private val messageHandlerPlugins: PluginPoint<MessageHandlerPlugin>,
) : Autoconsent {

    private lateinit var autoconsentJs: String

    override fun injectAutoconsent(webView: WebView) {
        Timber.d("MARCOS injecting autoconsent")
        webView.evaluateJavascript("javascript:${getFunctionsJS()}", null)
    }

    override fun addJsInterface(webView: WebView) {
        webView.addJavascriptInterface(AutoconsentInterface(messageHandlerPlugins, webView), "MARCOS")
    }

    private fun getFunctionsJS(): String {
        if (!this::autoconsentJs.isInitialized) {
            autoconsentJs = JsReader.loadJs("autoconsent-bundle.js")
        }
        return autoconsentJs
    }
}
