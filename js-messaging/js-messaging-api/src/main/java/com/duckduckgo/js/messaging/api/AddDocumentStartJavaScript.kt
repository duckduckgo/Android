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

import android.webkit.WebView

/**
 * Plugin interface for injecting JavaScript code that executes at document start.
 * * Allows plugins to inject JavaScript that will be executed before any other scripts on the page.
 * Useful for privacy protections and that need to run as early as possible and/or on iframes.
 */
interface AddDocumentStartJavaScript {
    suspend fun addDocumentStartJavaScript(webView: WebView)

    val context: String
}

/**
 * Strategy interface for script injection logic.
 * Allows different implementations to provide their own injection behavior.
 */
interface AddDocumentStartJavaScriptScriptStrategy {
    /**
     * Determines whether script injection should proceed (i.e. by checking feature flags).
     * @return true if injection is allowed, false otherwise
     */
    suspend fun canInject(): Boolean

    /**
     * Provides the script string to be injected.
     * @return the JavaScript code to inject
     */
    suspend fun getScriptString(): String

    /**
     * Defines the allowed origin rules for script injection.
     * @return set of allowed origin patterns
     */
    val allowedOriginRules: Set<String>

    val context: String
}

interface AddDocumentStartScriptDelegate {
    /**
     * Creates an AddDocumentStartJavaScriptPlugin implementation with the given [AddDocumentStartJavaScriptScriptStrategy].
     * @param strategy the strategy to use for determining injection behavior
     * @return [AddDocumentStartJavaScript] implementation
     */
    fun createPlugin(strategy: AddDocumentStartJavaScriptScriptStrategy): AddDocumentStartJavaScript
}
