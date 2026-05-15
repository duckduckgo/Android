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

package com.duckduckgo.browsermode.api.profile

import com.duckduckgo.browsermode.api.BrowserMode

/**
 * Owns the WebView [androidx.webkit.Profile] bound to each [BrowserMode].
 *
 * [initialize] is called once at app start. All other methods suspend until initialisation has
 * completed, so callers never observe an unprepared manager.
 */
interface WebViewProfileManager {

    /**
     * Loads persisted profile state. Called once at startup; subsequent calls return immediately.
     */
    suspend fun initialize()

    /**
     * Profile name currently bound to [mode]. Returns "Default" when
     * MultiProfile is unsupported on the device. Suspends until [initialize] has completed.
     */
    suspend fun getProfileName(mode: BrowserMode): String

    /**
     * Schedule async profile removal and creation of a new one for the supplied [mode].
     * No-op when MultiProfile is unsupported. Returns true if the rotation succeeded. Suspends
     * until [initialize] has completed.
     */
    suspend fun clearAndRotateProfile(mode: BrowserMode): Boolean

    /**
     * Removes any profiles on disk that aren't the currently-active one for their mode.
     * Suspends until [initialize] has completed.
     */
    suspend fun cleanupStaleProfiles()
}