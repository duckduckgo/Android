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
class PerformanceMetricsScriptJsInjectorPlugin @Inject constructor(): JsInjectorPlugin {
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
        val script = "var fcp = performance.getEntriesByType('paint').find(entry => entry.name === 'first-contentful-paint');" +
            "fcp ? fcp.startTime : 'FCP not available';"

        webView.evaluateJavascript(
            script,
        ) { value ->
            if ("null" != value) {
                val fcpTime = value.toDouble()
                site?.recordFirstContentfulPaint(fcpTime)
                Timber.d("PerfMetrics -> First Contentful Paint: $fcpTime ms")
            } else {
                Timber.d("PerfMetrics -> First Contentful Paint not available")
            }
        }
    }
}
