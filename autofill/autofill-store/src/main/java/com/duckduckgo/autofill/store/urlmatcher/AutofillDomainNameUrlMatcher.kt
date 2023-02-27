/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.store.urlmatcher

import androidx.core.net.toUri
import com.duckduckgo.app.global.extractDomain
import com.duckduckgo.app.global.normalizeScheme
import com.duckduckgo.autofill.api.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.api.urlmatcher.AutofillUrlMatcher.ExtractedUrlParts
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber

class AutofillDomainNameUrlMatcher @Inject constructor() : AutofillUrlMatcher {

    override fun extractUrlPartsForAutofill(originalUrl: String?): ExtractedUrlParts {
        if (originalUrl == null) return unextractable()

        val normalizedUrl = originalUrl.normalizeScheme()
        return try {
            val httpUrl = normalizedUrl.toHttpUrl()
            val eTldPlus1 = httpUrl.topPrivateDomain()
            val domain = originalUrl.extractDomain()
            val port = httpUrl.port
            val subdomain = determineSubdomain(domain, eTldPlus1)
            ExtractedUrlParts(eTldPlus1, subdomain, port)
        } catch (e: IllegalArgumentException) {
            Timber.d("Unable to parse e-tld+1 from [%s]", originalUrl)
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
