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

package com.duckduckgo.dataclearing.api.plugin

import com.duckduckgo.browsermode.api.BrowserMode

/**
 * Describes the kind of data being cleaned up.
 *
 * Each sealed subclass represents a category of data. Nested variants express scope within that
 * category (e.g. all data vs. a single tab) and, where applicable, the [BrowserMode] the operation
 * targets.
 */
sealed class ClearableData {

    /** Browser data: cookies, cache, web storage, etc. */
    sealed class BrowserData : ClearableData() {
        /** Clear every mode's browser data. */
        data object All : BrowserData()

        /** Clear all browser data scoped to [mode]. */
        data class AllForMode(val mode: BrowserMode) : BrowserData()

        /** Clear browser data linked to a single tab in [mode]. */
        data class SingleForMode(val tabId: String, val mode: BrowserMode) : BrowserData()
    }

    /** Tab data. */
    sealed class Tabs : ClearableData() {
        /** Clear every mode's tabs. */
        data object All : Tabs()

        /** Clear all tabs belonging to [mode]. */
        data class AllForMode(val mode: BrowserMode) : Tabs()

        /** Clear a single tab in [mode]. */
        data class SingleForMode(val tabId: String, val mode: BrowserMode) : Tabs()
    }

    /** Duck AI chats. */
    sealed class DuckChats : ClearableData() {
        /** Clear every mode's chats. */
        data object All : DuckChats()

        /** Clear all chats scoped to [mode]. */
        data class AllForMode(val mode: BrowserMode) : DuckChats()

        /**
         * A known set of Duck.ai chats in [mode], identified by their full chat URLs.
         * `setOf(oneUrl)` is the single-chat case; `emptySet()` is a valid no-op.
         */
        data class SelectedForMode(val chatUrls: Set<String>, val mode: BrowserMode) : DuckChats()
    }
}
