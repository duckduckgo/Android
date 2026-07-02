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
 * Whether AI-generated images are hidden from image search results.
 *
 * [serpCode] is the SERP's fixed encoding for this setting (the `kbj` value: "1" hides, "-1" shows) and is
 * used only to read/write the value the SERP exchanges. It is deliberately NOT used to derive radio-button
 * positions in the settings dialog — the dialog drives its selection off the order it displays options in.
 */
enum class HideAiGeneratedImages(val serpCode: String) {
    ON("1"),
    OFF("-1"),
    ;

    companion object {
        // The SERP key carrying the hide-AI-generated-images value in the serpSettings blob.
        const val SERP_SETTINGS_KEY = "kbj"

        // Maps the SERP-provided code to its option, defaulting to OFF (show) when absent or unrecognised.
        fun fromSerpCode(serpCode: String?): HideAiGeneratedImages =
            entries.firstOrNull { it.serpCode == serpCode } ?: OFF
    }
}
