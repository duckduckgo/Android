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

package com.duckduckgo.autofill.impl.encoding

import android.icu.text.IDNA
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.logcat

interface UrlUnicodeNormalizer {
    fun normalizeAscii(url: String?): String?
    fun normalizeUnicode(url: String?): String?
}

@ContributesBinding(AppScope::class)
class UrlUnicodeNormalizerImpl @Inject constructor() : UrlUnicodeNormalizer {

    override fun normalizeAscii(url: String?): String? {
        if (url == null) return null

        val originalScheme = url.scheme() ?: ""
        val noScheme = url.removePrefix(originalScheme)

        val sb = StringBuilder()
        val info = IDNA.Info()
        IDNA.getUTS46Instance(IDNA.DEFAULT).nameToASCII(noScheme, sb, info)
        if (info.hasErrors()) {
            logcat { "Unable to convert to ASCII: $url" }
            return url
        }
        return "${originalScheme}$sb"
    }

    override fun normalizeUnicode(url: String?): String? {
        if (url == null) return null

        val sb = StringBuilder()
        val info = IDNA.Info()
        IDNA.getUTS46Instance(IDNA.DEFAULT).nameToUnicode(url, sb, info)
        if (info.hasErrors()) {
            logcat { "Unable to convert to unicode: $url" }
            return url
        }
        return sb.toString()
    }
}

private fun String.scheme(): String? {
    if (this.startsWith("http://") || this.startsWith("https://")) {
        return this.substring(0, this.indexOf("://") + 3)
    }
    return null
}
