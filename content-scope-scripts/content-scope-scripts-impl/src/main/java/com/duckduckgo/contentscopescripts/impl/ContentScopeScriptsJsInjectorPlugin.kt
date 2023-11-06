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

import android.webkit.WebView
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.JsMessaging
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Named

@ContributesMultibinding(AppScope::class)
class ContentScopeScriptsJsInjectorPlugin @Inject constructor(
    private val coreContentScopeScripts: CoreContentScopeScripts,
    @Named("ContentScopeScripts") private val jsMessaging: JsMessaging,
) : JsInjectorPlugin {
    override fun onPageStarted(webView: WebView, url: String?) {
        if (coreContentScopeScripts.isEnabled()) {
            webView.evaluateJavascript("javascript:${getScript()}", null)
        }
    }

    override fun onPageFinished(webView: WebView, url: String?) {
        // NOOP
    }
    private fun getSecretKeyValuePair() = "\"messageSecret\":\"${jsMessaging.secret}\""
    private fun getCallbackKeyValuePair() = "\"messageCallback\":\"${jsMessaging.callbackName}\""
    private fun getInterfaceKeyValuePair() = "\"messageInterface\":\"${jsMessaging.context}\""

    private fun getScript(): String {
        return coreContentScopeScripts.getScript()
            .replace(
                RealContentScopeScripts.messagingParameters,
                "${getSecretKeyValuePair()},${getCallbackKeyValuePair()},${getInterfaceKeyValuePair()}",
            )
    }
}
