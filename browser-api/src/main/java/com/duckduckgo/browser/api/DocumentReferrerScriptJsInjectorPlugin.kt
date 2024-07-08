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

package com.duckduckgo.browser.api

import android.webkit.WebView
import com.duckduckgo.app.global.model.Site
import timber.log.Timber

class DocumentReferrerScriptJsInjectorPlugin : JsInjectorPlugin {
    override fun onPageStarted(
        webView: WebView,
        url: String?,
        site: Site?
    ) {
        // NOOP
    }

    override fun onPageFinished(
        webView: WebView,
        url: String?,
        site: Site?
    ) {
        if (url != "about:blank") {
            webView.evaluateJavascript("document.referrer") { referrer ->
                Timber.d("Referrer: $referrer")
                site?.inferLoadContext(referrer)
                Timber.d("Site context inferred from referrer: ${site?.openerContext?.context ?: "nope"}")
            }
        }
    }
}
