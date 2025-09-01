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

package com.duckduckgo.js.messaging.api

import androidx.webkit.ScriptHandler
import com.duckduckgo.feature.toggles.api.Toggle

/**
 * Plugin interface for injecting JavaScript code that executes at document start.
 * * Allows plugins to inject JavaScript that will be executed before any other scripts on the page.
 * Useful for privacy protections and that need to run as early as possible and/or on iframes.
 */
interface AddDocumentStartJavaScriptPlugin {

    /**
     * Configures JavaScript injection for addDocumentStartJavaScript and executes it through [scriptInjector].
     * @param scriptInjector A suspend function that injects the script into the WebView.
     *                       Returns a [ScriptHandler] that can be used to remove the script later,
     *                       or null if injection failed.
     */
    suspend fun configureAddDocumentStartJavaScript(
        activeExperiments: List<Toggle>,
        scriptInjector: suspend (scriptString: String, allowedOriginRules: Set<String>) -> ScriptHandler?,
    )
}
