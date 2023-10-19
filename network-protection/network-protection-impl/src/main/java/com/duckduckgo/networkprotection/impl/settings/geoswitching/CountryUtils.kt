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

internal fun getEmojiForCountryCode(countryCode: String): String {
    return when (countryCode) {
        "uk" -> "🇬🇧"
        "fr" -> "🇫🇷"
        "ca" -> "🇨🇦"
        "us" -> "🇺🇸"
        "de" -> "🇩🇪"
        "es" -> "🇪🇸"
        else -> "🏳️"
    }
    /**
    val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
    val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))**/
}

internal fun getDisplayableCountry(countryCode: String): String {
    return Locale("", countryCode).displayCountry
}
