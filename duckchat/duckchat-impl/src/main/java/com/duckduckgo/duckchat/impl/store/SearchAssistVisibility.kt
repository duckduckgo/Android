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

package com.duckduckgo.duckchat.impl.store

/**
 * Search Assist visibility options.
 *
 * [serpCode] is the SERP's fixed encoding for this setting (the `kbe` value, "0".."3") and is used ONLY
 * to read the value the SERP sends. It is deliberately NOT used to derive radio-button positions in the
 * settings dialog — the dialog drives pre-selection and the chosen result off the order it actually
 * displays options in, so reordering them can never silently map to the wrong option.
 */
enum class SearchAssistVisibility(val serpCode: String) {
    NEVER("0"),
    ON_DEMAND("1"),
    SOMETIMES("2"),
    OFTEN("3"),
    ;

    companion object {
        // The SERP key carrying the search-assist visibility value in the serpSettings blob.
        const val SERP_SETTINGS_KEY = "kbe"

        // Returns null when nothing has been stored yet, so callers can distinguish "no selection" from a chosen option.
        fun fromName(name: String?): SearchAssistVisibility? =
            entries.firstOrNull { it.name == name }

        // Maps the SERP-provided code ("0".."3") to its option. Returns null for unknown/absent values.
        fun fromSerpCode(serpCode: String?): SearchAssistVisibility? =
            entries.firstOrNull { it.serpCode == serpCode }
    }
}
