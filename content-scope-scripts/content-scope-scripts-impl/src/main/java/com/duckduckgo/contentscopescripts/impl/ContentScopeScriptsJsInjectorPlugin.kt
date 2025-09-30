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
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.contentscopescripts.api.CoreContentScopeScripts
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ContentScopeScriptsJsInjectorPlugin @Inject constructor(
    private val coreContentScopeScripts: CoreContentScopeScripts,
) : JsInjectorPlugin {
    override fun onPageStarted(
        webView: WebView,
        url: String?,
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ) {
        if (coreContentScopeScripts.isEnabled()) {
            webView.evaluateJavascript("javascript:${coreContentScopeScripts.getScript(isDesktopMode, activeExperiments)}", null)
        }
    }

    override fun onPageFinished(
        webView: WebView,
        url: String?,
        site: Site?,
    ) {
        // NOOP
    }
}
