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

package com.duckduckgo.app.browser.easteregglogos

import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.LogPriority
import logcat.logcat

data class EasterEggLogo(
    val logo: String,
    val type: String,
)

interface EasterEggLogoJsMatcher {

    /**
     * JavaScript code to extract the Easter Egg logo from the page.
     */
    val js: String

    /**
     * Processes the result of the JavaScript evaluation.
     * Parses the result string to extract the logo type and URL.
     *
     * @param result The raw result from the JavaScript evaluation.
     * @return An EasterEggLogo object if the result is valid, or null if it cannot be parsed.
     */
    fun processJsResult(result: String?): EasterEggLogo?
}

@ContributesBinding(FragmentScope::class)
class RealEasterEggLogoJsMatcher @Inject constructor() : EasterEggLogoJsMatcher {

    /**
     * JavaScript code to extract the Easter Egg logo from the page.
     * It looks for an element with the class 'js-logo-ddg' and retrieves its background image or data attribute
     * and returns a string in the format "type|url", where type is either "themed" or "normal".
     */
    override val js = """
        (function() {
            const element = document.querySelector('.js-logo-ddg');
            if (!element) {
                console.log('DDG logo element not found');
                return null;
            }
            if (element.dataset.dynamicLogo) {
                return 'themed|' + element.dataset.dynamicLogo;
            }
            const style = window.getComputedStyle(element);
            const bgImage = style.backgroundImage;
            if (bgImage && bgImage.includes("url(\"")) {
                const url = bgImage.split('url("')[1].split('")')[0];
                return 'normal|' + url;
            }
            return null;
        })();
    """.trimIndent()

    override fun processJsResult(result: String?): EasterEggLogo? {
        logcat { "Raw JS result: $result" }
        val unquoted = result?.removeSurrounding("\"")
        logcat { "Unquoted result: $unquoted" }
        return if (!unquoted.isNullOrBlank() && unquoted != "null") {
            val parts = unquoted.split("|", limit = 2)
            if (parts.size == 2) {
                val logoType = parts[0]
                val logoUrl = parts[1]
                logcat { "Parsed logo - type: $logoType, url: $logoUrl" }
                EasterEggLogo(logoUrl, logoType)
            } else {
                logcat(LogPriority.ERROR) { "Invalid logo format: $unquoted" }
                null
            }
        } else {
            logcat(LogPriority.WARN) { "Logo extraction returned null or blank. Raw: $result" }
            null
        }
    }
} 
