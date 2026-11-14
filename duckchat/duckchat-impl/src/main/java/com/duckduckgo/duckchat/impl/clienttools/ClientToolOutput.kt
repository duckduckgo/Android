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

package com.duckduckgo.duckchat.impl.clienttools

/**
 * Fits tool values to the v1 contract limits. Duck.ai rejects a value over any limit, which ends the answer.
 * Lengths are UTF-16 units, so a cut value never exceeds the contract's code point limit.
 */
object ClientToolOutput {
    const val MAX_TITLE_LENGTH = 256
    const val MAX_URL_LENGTH = 1280

    fun title(title: String?): String = truncate(title.orEmpty(), MAX_TITLE_LENGTH)

    /** Drops user info, query and fragment, which can carry credentials or one-time links. */
    fun url(url: String): String = truncate(withoutUserInfo(withoutQueryOrFragment(url)), MAX_URL_LENGTH)

    fun truncate(
        value: String,
        maxLength: Int,
    ): String {
        if (value.length <= maxLength) return value
        val length = if (value[maxLength - 1].isHighSurrogate()) maxLength - 1 else maxLength
        return value.substring(0, length)
    }

    private fun withoutQueryOrFragment(url: String): String {
        val cut = url.indexOfFirst { it == '?' || it == '#' }
        return if (cut >= 0) url.substring(0, cut) else url
    }

    private fun withoutUserInfo(url: String): String {
        val authorityStart = url.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: return url
        val authorityEnd = url.indexOf('/', authorityStart).takeIf { it >= 0 } ?: url.length
        val at = url.lastIndexOf('@', authorityEnd - 1)
        return if (at >= authorityStart) url.substring(0, authorityStart) + url.substring(at + 1) else url
    }
}
