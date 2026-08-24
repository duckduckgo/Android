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

package com.duckduckgo.settings.api

/**
 * Whether the SERP filters explicit results.
 *
 * [serpCode] is the SERP's fixed encoding for this setting (the `kp` value: "-1" on, "-2" off).
 */
enum class SafeSearch(override val serpCode: String) : SerpSetting {
    ON("-1"),
    OFF("-2"),
    ;

    // A getter, not an initializer: enum entries are constructed before the companion object, so reading
    // SERP_SETTINGS_KEY eagerly does not compile.
    override val serpKey get() = SERP_SETTINGS_KEY

    companion object {
        // The SERP key carrying the safe-search value in the serpSettings blob.
        const val SERP_SETTINGS_KEY = "kp"

        // Returns null for unknown/absent values, so callers can apply their own default.
        fun fromSerpCode(serpCode: String?): SafeSearch? =
            entries.firstOrNull { it.serpCode == serpCode }
    }
}
