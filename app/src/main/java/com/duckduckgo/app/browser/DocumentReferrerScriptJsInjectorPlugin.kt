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

package com.duckduckgo.app.browser

import android.webkit.WebView
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DocumentReferrerScriptJsInjectorPlugin @Inject constructor(): JsInjectorPlugin {
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
                val sanitizedReferrer = referrer?.removeSurrounding("\"")
                Timber.d("OpenerContext referrer: $sanitizedReferrer")
                site?.inferOpenerContext(sanitizedReferrer)
                Timber.d("OpenerContext inferred from referrer: ${site?.openerContext?.context ?: "nope"}")
            }
        }
    }
}
