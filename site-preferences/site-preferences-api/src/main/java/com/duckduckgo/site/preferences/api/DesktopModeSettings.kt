/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.site.preferences.api

/**
 * Per-site "open in desktop mode" preference, consumed by the browser. `domain` is a site key:
 * eTLD+1, or the raw host when there is no registrable domain (IPs, localhost).
 */
interface DesktopModeSettings {

    /**
     * Correct from the very first navigation: reads the in-memory cache and, only during the brief
     * window before the cache is primed at startup, falls back to a direct DB read so a remembered
     * site never loads in mobile mode by accident. Suspends — call it from a coroutine.
     */
    suspend fun isDesktopModeRemembered(domain: String): Boolean

    /**
     * Synchronous, in-memory-only snapshot for callers that cannot suspend — the main-thread page-load
     * path, where Room forbids DB access. May be momentarily stale only during the startup priming
     * window; it self-corrects on the next navigation event.
     */
    fun isDesktopModeRememberedInCache(domain: String): Boolean

    /** Remember that [domain] should always open in desktop mode. */
    fun rememberDesktopMode(domain: String)

    /** Forget the desktop-mode preference for [domain]. */
    fun forgetDesktopMode(domain: String)
}
