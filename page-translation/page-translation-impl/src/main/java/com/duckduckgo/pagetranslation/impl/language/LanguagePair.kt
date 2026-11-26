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

package com.duckduckgo.pagetranslation.impl.language

/**
 * A translation direction, as language codes.
 */
data class LanguagePair(
    val source: String,
    val target: String,
) {
    /** The pair as the manifest and the language pack files name it, e.g. "es-en". */
    val key: String
        get() = "$source-$target"

    companion object {
        /** The pair a [key] names, or null when it doesn't name one. */
        fun fromKey(key: String): LanguagePair? {
            val languages = key.split('-').takeIf { it.size == 2 && it.none(String::isEmpty) } ?: return null

            return LanguagePair(languages[0], languages[1])
        }
    }
}
