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

package com.duckduckgo.browser.api.wideevents

/**
 * Tracks a post-idle session — NTP or LUT shown after an idle return — as a Wide Event flow.
 */
interface PostIdleSessionWideEvent {

    /** Starts a session. Aborts any prior session. */
    fun onSurfaceShown(surface: Surface)

    /** Non-terminal: the user touched the page. */
    fun onPageEngaged()

    /** Non-terminal: the user pressed back. */
    fun onBackPressed()

    /** Terminal SUCCESS: the user submitted a search or chat query. */
    fun onBarUsed()

    /** Terminal SUCCESS: the user tapped the return-to-page hatch (NTP only). */
    fun onReturnToPageTapped()

    /** Terminal SUCCESS: the user opened the tab switcher. */
    fun onTabSwitcherSelected()

    /** Terminal SUCCESS: the user tapped a favorite (NTP only). */
    fun onFavoriteSelected()

    enum class Surface(val value: String) {
        NTP("ntp"),
        LUT("lut"),
    }
}
