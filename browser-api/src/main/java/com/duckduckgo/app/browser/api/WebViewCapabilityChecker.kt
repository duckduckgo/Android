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

package com.duckduckgo.app.browser.api

/**
 * Allows WebView capabilities to be queried.
 * WebView capabilities depend on various conditions, such as WebView version, feature flags etc...
 * Capabilities can change over time, so it's recommended to always check immediately before trying to use that capability.
 */
interface WebViewCapabilityChecker {

    /**
     * Check if a particular capability is currently supported by the WebView
     */
    suspend fun isSupported(capability: WebViewCapability, additionalCompatibilityChecks: Boolean = true): Boolean

    /**
     * WebView capabilities, which can be provided to [isSupported]
     */
    sealed interface WebViewCapability {
        /**
         * WebMessageListener
         * The ability to post web messages to JS, and receive web messages from JS
         */
        data object WebMessageListener : WebViewCapability

        /**
         * DocumentStartJavaScript
         * The ability to inject Javascript which is guaranteed to be executed first on the page, and available in all iframes
         */
        data object DocumentStartJavaScript : WebViewCapability
    }
}
