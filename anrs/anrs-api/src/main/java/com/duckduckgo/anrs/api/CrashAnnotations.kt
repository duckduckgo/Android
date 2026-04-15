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

package com.duckduckgo.anrs.api

/**
 * Updates named annotations that are captured in a crash minidump.
 *
 * ## What annotations are for
 *
 * Annotations capture the **current state** of the app at crash time. Each key holds
 * exactly one value; calling [set] again overwrites the previous value. Use annotations
 * to answer "what was the app doing when it crashed?":
 *   - Current URL: `browser_url`
 *   - Navigation state: `browser_nav_state` ("loading", "loaded", "error")
 *   - VPN tunnel state: `vpn_tunnel_state`
 *
 * For ordered event sequences ("what sequence of events led to the crash?") use
 * [CrashBreadcrumbs.add] instead.
 *
 * ## Key registration
 *
 * Every key passed to [set] MUST be declared in a [CrashAnnotationContributor] that
 * is active in this process. Unregistered keys are silently dropped from the minidump.
 * See [CrashAnnotationContributor] for how to register keys.
 *
 * ## Key/value limits
 *
 * Keys and values are each limited to 255 bytes (UTF-8). Values exceeding this are
 * silently truncated by the native layer. Prefer short, structured values (enum names,
 * integers, short URLs) over free text.
 *
 * ## Threading
 *
 * [set] is safe to call from any thread. Calls before Crashpad initialises are
 * silently dropped.
 *
 * ## Example
 *
 * ```kotlin
 * // In a WebViewClient or ViewModel:
 * override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
 *     // Annotation: always reflects the current URL at crash time.
 *     crashAnnotations.set(BrowserAnnotationKeys.BROWSER_URL, url)
 *     crashAnnotations.set(BrowserAnnotationKeys.BROWSER_NAV_STATE, "loading")
 * }
 * ```
 */
interface CrashAnnotations {
    /**
     * Updates [key] to [value] in the crash report.
     *
     * Has no effect if [key] was not pre-registered via a [CrashAnnotationContributor],
     * or if Crashpad has not yet been initialised.
     *
     * @param key   `snake_case`, feature-prefixed identifier, e.g. `"browser_url"`. Max 255 bytes.
     * @param value Current state value. Max 255 bytes; longer values are truncated by the native layer.
     */
    fun set(key: String, value: String)
}
