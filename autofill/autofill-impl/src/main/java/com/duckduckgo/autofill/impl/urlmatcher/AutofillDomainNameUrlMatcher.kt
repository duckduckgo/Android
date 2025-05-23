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

package com.duckduckgo.autofill.impl.urlmatcher

import androidx.core.net.toUri
import com.duckduckgo.autofill.impl.encoding.UrlUnicodeNormalizer
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher.ExtractedUrlParts
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.common.utils.normalizeScheme
import javax.inject.Inject
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * This interface is used to clean and match URLs for autofill purposes. It can offer support for:
 *    - When we have a full URL and might have to clean this up before we can save it.
 *    - When we have two URLs and need to determine if they should be treated as matching for autofill purposes.
 */
interface AutofillUrlMatcher {

    /**
     * This method tries to extract the parts of a URL that are relevant for autofill.
     */
    fun extractUrlPartsForAutofill(originalUrl: String?): ExtractedUrlParts

    /**
     * This method tries to determine if two URLs are matching for autofill purposes.
     * @return true if the two URLs are matching for autofill purposes, false otherwise.
     */
    fun matchingForAutofill(
        visitedSite: ExtractedUrlParts,
        savedSite: ExtractedUrlParts,
    ): Boolean

    /**
     * This method tries to clean up a raw URL.
     * @return a `String` containing host:port for the given raw URL.
     */
    fun cleanRawUrl(rawUrl: String): String

    /**
     * Data class to hold the extracted parts of a URL.
     * @param eTldPlus1 the eTldPlus1 of the URL, an important component when considering whether two URLs are matching for autofill or not. IDNA-encoded if domain contains non-ascii.
     * @param userFacingETldPlus1 the user-facing eTldPlus1. eTldPlus1 might be IDNA-encoded whereas this will be Unicode-encoded, and therefore might contain non-ascii characters.
     * @param subdomain the subdomain of the URL or null if there was no subdomain.
     * @param port the port of the URL or null if there was no explicit port.
     */
    data class ExtractedUrlParts(
        val eTldPlus1: String?,
        val userFacingETldPlus1: String?,
        val subdomain: String?,
        val port: Int? = null,
    )
}

class AutofillDomainNameUrlMatcher @Inject constructor(
    private val unicodeNormalizer: UrlUnicodeNormalizer,
) : AutofillUrlMatcher {

    override fun extractUrlPartsForAutofill(originalUrl: String?): ExtractedUrlParts {
        if (originalUrl == null) return unextractable()

        val normalizedUrl = unicodeNormalizer.normalizeAscii(originalUrl)?.normalizeScheme() ?: return unextractable()

        return try {
            val httpUrl = normalizedUrl.toHttpUrl()
            val eTldPlus1 = httpUrl.topPrivateDomain()
            val domain = originalUrl.extractDomain()
            val port = httpUrl.port
            val subdomain = determineSubdomain(domain, eTldPlus1)
            ExtractedUrlParts(
                eTldPlus1 = eTldPlus1,
                userFacingETldPlus1 = unicodeNormalizer.normalizeUnicode(eTldPlus1),
                subdomain = subdomain,
                port = port,
            )
        } catch (e: IllegalArgumentException) {
            logcat { "Unable to parse e-tld+1 from [$originalUrl]" }
            unextractable()
        }
    }

    private fun determineSubdomain(
        domain: String?,
        eTldPlus1: String?,
    ): String? {
        if (eTldPlus1 == null) return null

        val subdomain = domain?.replace(eTldPlus1 ?: "", "", ignoreCase = true)?.removeSuffix(".")
        if (subdomain?.isBlank() == true) return null

        return subdomain
    }

    override fun matchingForAutofill(
        visitedSite: ExtractedUrlParts,
        savedSite: ExtractedUrlParts,
    ): Boolean {
        // ports must match (both being null is considered a match)
        if (visitedSite.port != savedSite.port) return false

        // e-tld+1 must match
        return identicalEffectiveTldPlusOne(visitedSite, savedSite)
    }

    override fun cleanRawUrl(rawUrl: String): String {
        val uri = rawUrl.normalizeScheme().toUri()
        val host = uri.host ?: return rawUrl
        val port = if (uri.port != -1) ":${uri.port}" else ""
        return "$host$port"
    }

    private fun identicalEffectiveTldPlusOne(
        visitedSite: ExtractedUrlParts,
        savedSite: ExtractedUrlParts,
    ): Boolean {
        return visitedSite.eTldPlus1.equals(savedSite.eTldPlus1, ignoreCase = true)
    }

    private fun unextractable(): ExtractedUrlParts {
        return ExtractedUrlParts(null, null, null)
    }
}
