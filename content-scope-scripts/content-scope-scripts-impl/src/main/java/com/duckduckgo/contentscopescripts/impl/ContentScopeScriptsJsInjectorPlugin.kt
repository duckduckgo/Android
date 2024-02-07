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
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ContentScopeScriptsJsInjectorPlugin @Inject constructor(
    private val coreContentScopeScripts: CoreContentScopeScripts,
) : JsInjectorPlugin {
    private var script: ScriptHandler? = null

    override fun onInit(webView: WebView, site: Site?) {
        script?.let {
            it.remove()
        }
        // if (coreContentScopeScripts.isEnabled()) {
        script = WebViewCompat.addDocumentStartJavaScript(webView, "${coreContentScopeScripts.getScript(site)}", setOf("*"))
        // }
    }

    override fun onPageStarted(webView: WebView, url: String?, site: Site?) {
        // NOOP
    }

    override fun onPageFinished(webView: WebView, url: String?, site: Site?) {
        // NOOP
    }
}
