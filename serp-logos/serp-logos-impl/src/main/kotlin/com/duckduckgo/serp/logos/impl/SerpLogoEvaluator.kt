/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.serp.logos.impl

import android.webkit.WebView
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.LogPriority
import logcat.logcat

sealed class SerpLogo {
    data class EasterEgg(val logoUrl: String) : SerpLogo()
    data object Normal : SerpLogo()
}

interface SerpLogoEvaluator {

    fun evaluate(
        webView: WebView,
        onSerpLogoEvaluated: (SerpLogo) -> Unit,
    )
}

@ContributesBinding(FragmentScope::class)
class RealSerpLogoEvaluator @Inject constructor(
    private val serpLogoJavascriptInterface: SerpLogoJavascriptInterface,
) : SerpLogoEvaluator {

    override fun evaluate(
        webView: WebView,
        onSerpLogoEvaluated: (SerpLogo) -> Unit,
    ) {
        webView.evaluateJavascript(serpLogoJavascriptInterface.js) { result ->
            onSerpLogoEvaluated(evaluateSerpLogoType(result))
        }
    }

    private fun evaluateSerpLogoType(result: String?): SerpLogo {
        logcat { "Raw JS result: $result" }
        val unquoted = result?.removeSurrounding("\"")
        logcat { "Unquoted result: $unquoted" }
        return if (!unquoted.isNullOrBlank() && unquoted != "null") {
            val parts = unquoted.split("|", limit = 2)
            if (parts.size == 2) {
                val logoType = parts[0]
                val logoUrl = parts[1]
                logcat { "Parsed logo - type: $logoType, url: $logoUrl" }
                if (logoType == "themed") {
                    SerpLogo.EasterEgg("https://${AppUrl.Url.HOST}$logoUrl")
                } else if (logoType == "normal") {
                    SerpLogo.Normal
                } else {
                    logcat(LogPriority.WARN) { "Unknown logo type: $logoType" }
                    SerpLogo.Normal
                }
            } else {
                logcat(LogPriority.ERROR) { "Invalid logo format: $unquoted" }
                SerpLogo.Normal
            }
        } else {
            logcat(LogPriority.WARN) { "Logo extraction returned null or blank. Raw: $result" }
            SerpLogo.Normal
        }
    }
}
