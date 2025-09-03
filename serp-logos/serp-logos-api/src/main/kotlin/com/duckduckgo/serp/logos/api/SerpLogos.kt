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

package com.duckduckgo.serp.logos.api

import android.webkit.WebView

/**
 * Public interface for evaluating SERP logos
 */
interface SerpLogos {

    /**
     * Evaluates the current SERP page in the provided WebView by injecting JavaScript
     * to determine if a special logo (e.g., Easter Egg) should be displayed.
     *
     * If there are any issues during the evaluation, it defaults to returning [SerpLogo.Normal].
     *
     * @param webView The WebView containing the SERP page to evaluate
     * @return The appropriate [SerpLogo] found in the page
     */
    suspend fun extractSerpLogo(webView: WebView): SerpLogo
}
