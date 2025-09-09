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
        return normalizeUrl(url) { hostname, sb, info ->
            IDNA.getUTS46Instance(IDNA.DEFAULT).nameToASCII(hostname, sb, info)
        }
    }

    override fun normalizeUnicode(url: String?): String? {
        return normalizeUrl(url) { hostname, sb, info ->
            IDNA.getUTS46Instance(IDNA.DEFAULT).nameToUnicode(hostname, sb, info)
        }
    }

    private fun normalizeUrl(
        url: String?,
        idnaProcessor: (hostname: String, sb: StringBuilder, info: IDNA.Info) -> Unit,
    ): String? {
        if (url == null) return null

        val originalScheme = url.scheme() ?: ""
        val noScheme = url.removePrefix(originalScheme)

        // Extract just the hostname/domain part for IDNA processing
        val hostEndIndex = noScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val hostname = if (hostEndIndex == -1) noScheme else noScheme.substring(0, hostEndIndex)
        val pathAndQuery = if (hostEndIndex == -1) "" else noScheme.substring(hostEndIndex)

        val sb = StringBuilder()
        val info = IDNA.Info()
        idnaProcessor(hostname, sb, info)
        if (info.hasErrors()) {
            logcat { "Unable to convert hostname: $hostname" }
            return url
        }
        return "${originalScheme}$sb$pathAndQuery"
    }
}

private fun String.scheme(): String? {
    if (this.startsWith("http://") || this.startsWith("https://")) {
        return this.substring(0, this.indexOf("://") + 3)
    }
    return null
}
