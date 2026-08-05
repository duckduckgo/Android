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
import com.duckduckgo.common.utils.performance.PerfTracer
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ContentScopeScriptsJsInjectorPlugin @Inject constructor(
    private val coreContentScopeScripts: CoreContentScopeScripts,
    private val perfTracer: PerfTracer,
) : JsInjectorPlugin {
    override fun onPageStarted(
        webView: WebView,
        url: String?,
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ) {
        if (coreContentScopeScripts.isEnabled()) {
            val script = coreContentScopeScripts.getScript(isDesktopMode, activeExperiments)
            if (perfTracer.isEnabled()) {
                injectTraced(webView, script)
            } else {
                webView.evaluateJavascript("javascript:$script", null)
            }
        }
    }

    /**
     * Brackets the injection with an async section that closes from [WebView.evaluateJavascript]'s
     * callback, so the slice spans dispatch plus the renderer's parse/compile/execute rather than
     * just the JNI hand-off. The trade is that it also includes renderer queueing — subtract
     * `window.__ddgPerf.execMs` to separate waiting from executing.
     *
     * Only used while a trace is being recorded: supplying a callback makes WebView serialise the
     * script's completion value back across the bridge on every page load, which shipped builds
     * should not pay for.
     *
     * The cookie lives in the lambda's closure rather than a field, so concurrent tabs cannot collide
     * and a callback that never arrives (WebView destroyed mid-flight) costs only its own sample
     * instead of blocking later ones.
     */
    private fun injectTraced(
        webView: WebView,
        script: String,
    ) {
        val cookie = perfTracer.beginAsyncSection(TRACE_DISPATCH_JAVASCRIPT)
        webView.evaluateJavascript("javascript:$script") {
            perfTracer.endAsyncSection(TRACE_DISPATCH_JAVASCRIPT, cookie)
        }
    }

    override fun onPageFinished(
        webView: WebView,
        url: String?,
        site: Site?,
    ) {
        // NOOP
    }

    companion object {
        private const val TRACE_DISPATCH_JAVASCRIPT = "ddg.contentScope.dispatchJavascript"
    }
}
