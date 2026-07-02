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
 * Per-site "open in desktop mode" preference, consumed by the browser. Callers pass a raw page [url];
 * the implementation derives the site key (eTLD+1, or the raw host when there is no registrable
 * domain — IPs, localhost) internally, so call sites cannot disagree on the key.
 */
interface DesktopModeSettings {

    /**
     * Correct from the very first navigation: reads the in-memory cache and, only during the brief
     * window before the cache is primed at startup, falls back to a direct DB read so a remembered
     * site never loads in mobile mode by accident.
     */
    suspend fun isDesktopModeRemembered(url: String): Boolean

    /**
     * Synchronous, best-effort read for callers that cannot suspend. May be momentarily stale
     * only during the startup priming window. It self-corrects on the next navigation event.
     */
    fun isDesktopModeRememberedSync(url: String): Boolean

    /** Remember that the site at [url] should always open in desktop mode. */
    fun rememberDesktopMode(url: String)

    /** Forget the desktop-mode preference for the site at [url]. */
    fun forgetDesktopMode(url: String)
}
