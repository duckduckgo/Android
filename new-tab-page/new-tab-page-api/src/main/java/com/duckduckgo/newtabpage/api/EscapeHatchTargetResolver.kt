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

package com.duckduckgo.newtabpage.api

import com.duckduckgo.browsermode.api.BrowserMode

/**
 * Resolves which tab the NTP escape hatch should offer to return to.
 *
 * The hatch only ever renders in Regular mode, so an [EscapeHatchTarget] with [EscapeHatchTarget.mode]
 * == [BrowserMode.FIRE] also implies that tapping the hatch must switch into Fire mode to open it.
 */
interface EscapeHatchTargetResolver {
    /** Returns the tab to offer, or null when there is none (including whenever the app is in Fire mode). */
    suspend fun resolve(): EscapeHatchTarget?
}

/**
 * @param tabId id of the tab to return to.
 * @param mode the [BrowserMode] that owns the tab (the database it lives in); the mode the browser
 * must be in to open it.
 */
data class EscapeHatchTarget(
    val tabId: String,
    val mode: BrowserMode,
)
