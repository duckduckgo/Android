/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.settings.geoswitching

import java.util.*

private const val REGIONAL_INDICATOR_OFFSET = 0x1F1A5

/*
 * The regional indicators go from 0x1F1E6 (A) to 0x1F1FF (Z).
 * This is the A regional indicator value minus 65 decimal so
 * that we can just add this to the A-Z char
 */
internal fun getEmojiForCountryCode(countryCode: String): String {
    @Suppress("NAME_SHADOWING")
    val countryCode = countryCode.uppercase()

    if (countryCode.isBlank()) return "🏳️"
    if (countryCode.length > 2) return "🏳️"
    val (first, second) = countryCode.toCharArray().run {
        this[0] to this[1]
    }
    if (first < 'A' || first > 'Z') return "🏳️"
    if (second < 'A' || second > 'Z') return "🏳️"

    return countryCode.uppercase()
        .split("")
        .filter { it.isNotBlank() }
        .map { it.codePointAt(0) + REGIONAL_INDICATOR_OFFSET }
        .joinToString("") { String(Character.toChars(it)) }
}

internal fun getDisplayableCountry(countryCode: String): String {
    return Locale("", countryCode).displayCountry
}
