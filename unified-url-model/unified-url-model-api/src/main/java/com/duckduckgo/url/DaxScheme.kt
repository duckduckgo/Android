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

package com.duckduckgo.url

/**
 * Closed set of URL schemes the app reasons about. Replaces the naked scheme String and the
 * scattered `scheme == UrlScheme.http` checks. [OTHER] absorbs everything we don't special-case
 * (`file://`, `about:`, `mailto:`, a missing scheme, …) so the set stays small.
 */
enum class DaxScheme(val value: String) {
    HTTP("http"),
    HTTPS("https"),
    DUCK("duck"),
    OTHER(""),
    ;

    companion object {
        fun fromValue(scheme: String?): DaxScheme = when (scheme?.lowercase()) {
            HTTP.value -> HTTP
            HTTPS.value -> HTTPS
            DUCK.value -> DUCK
            else -> OTHER
        }
    }
}
