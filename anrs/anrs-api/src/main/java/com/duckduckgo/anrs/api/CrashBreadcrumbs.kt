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
 * Records timestamped events in a ring buffer that is captured in a crash minidump.
 *
 * ## What breadcrumbs are for
 *
 * Breadcrumbs capture the **sequence of events** leading up to a crash, stored in a
 * ring buffer of the last 10 entries. Use breadcrumbs to answer "what happened just
 * before the crash?":
 *   - "user navigated to an external URL"
 *   - "WebView renderer process died"
 *   - "VPN connection dropped"
 *
 * For current-state values ("what state was the app in at crash time?") use
 * [CrashAnnotations.set] instead. It is common to use both: annotation for the
 * latest value, breadcrumb for the history.
 *
 * ## Key registration
 *
 * The 11 breadcrumb slots (`crumb_0`..`crumb_9` + `crumb_idx`) are pre-registered
 * automatically — you do NOT need a [CrashAnnotationContributor] for breadcrumbs.
 *
 * ## Ring buffer
 *
 * The buffer holds the last 10 entries. When full, the oldest entry is overwritten.
 * At crash time, read `crumb_0`..`crumb_9` and use `crumb_idx` (the next write
 * position) to reconstruct the chronological order.
 *
 * ## Entry format
 *
 * `[HH:mm:ss.SSS][TAG] message`
 *
 * The combined string is capped at 255 bytes (Crashpad's per-value limit).
 *
 * ## Threading
 *
 * [add] is safe to call from any thread. Do NOT call [add] from hot paths such as
 * scroll handlers or draw callbacks — breadcrumbs are for coarse-grained lifecycle
 * and navigation events.
 *
 * ## Example
 *
 * ```kotlin
 * // In a WebViewClient or ViewModel:
 * override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
 *     // Breadcrumb: records this navigation event in the sequence.
 *     crashBreadcrumbs.add("BROWSER", "page_started: $url")
 * }
 *
 * fun onRenderProcessGone(didCrash: Boolean) {
 *     crashBreadcrumbs.add("BROWSER", "renderer_gone crash=$didCrash")
 * }
 * ```
 */
interface CrashBreadcrumbs {
    /**
     * Appends a breadcrumb to the ring buffer.
     *
     * @param tag     Short identifier for the feature or component, e.g. `"BROWSER"`, `"VPN"`.
     *                Keep it under 20 characters — it counts toward the 255-byte entry limit.
     * @param message Human-readable event description, e.g. `"page_started: https://duck.ai"`.
     *                Truncated to fit within the 255-byte entry limit after the tag prefix.
     */
    fun add(tag: String, message: String)
}
