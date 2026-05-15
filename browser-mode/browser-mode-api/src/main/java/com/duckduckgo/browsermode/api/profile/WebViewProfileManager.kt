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
 * The manager bootstraps itself at app start. All methods suspend until that bootstrap has
 * completed, so callers never observe an unprepared manager.
 */
interface WebViewProfileManager {

    /**
     * Profile name currently bound to [mode]. Returns "Default" when
     * MultiProfile is unsupported on the device.
     */
    suspend fun getProfileName(mode: BrowserMode): String

    /**
     * Schedule async profile removal and creation of a new one for the supplied [mode].
     * No-op when MultiProfile is unsupported. Returns true if the rotation succeeded.
     */
    suspend fun clearAndRotateProfile(mode: BrowserMode): Boolean

    /**
     * Removes any profiles on disk that aren't the currently-active one for their mode.
     */
    suspend fun cleanupStaleProfiles()
}
