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

import com.duckduckgo.app.global.extractDomain
import com.duckduckgo.autofill.ui.urlmatcher.AutofillUrlMatcher
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber

class AutofillDomainNameUrlMatcher @Inject constructor() : AutofillUrlMatcher {

    override fun extractUrlPartsForAutofill(originalUrl: String): AutofillUrlMatcher.ExtractedUrlParts {
        val normalizedUrl = originalUrl.normalizeScheme()
        return try {
            val eTldPlus1 = normalizedUrl.toHttpUrl().topPrivateDomain()
            val domain = originalUrl.extractDomain()
            val subdomain = determineSubdomain(domain, eTldPlus1)
            AutofillUrlMatcher.ExtractedUrlParts(eTldPlus1, subdomain)
        } catch (e: IllegalArgumentException) {
            Timber.w("Unable to parse e-tld+1 from $originalUrl")
            AutofillUrlMatcher.ExtractedUrlParts(null, null)
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
        visitedSite: AutofillUrlMatcher.ExtractedUrlParts,
        savedSite: AutofillUrlMatcher.ExtractedUrlParts,
    ): Boolean {
        // e-tld+1 must match
        if (!identicalEffectiveTldPlusOne(visitedSite, savedSite)) return false

        // any of these rules can match
        return identicalSubdomains(visitedSite, savedSite) ||
            specialHandlingForWwwSubdomainOnSavedSite(visitedSite, savedSite) ||
            savedSiteHasNoSubdomain(savedSite)
    }

    private fun identicalEffectiveTldPlusOne(
        visitedSite: AutofillUrlMatcher.ExtractedUrlParts,
        savedSite: AutofillUrlMatcher.ExtractedUrlParts,
    ): Boolean {
        return visitedSite.eTldPlus1.equals(savedSite.eTldPlus1, ignoreCase = true)
    }

    private fun identicalSubdomains(
        visitedSite: AutofillUrlMatcher.ExtractedUrlParts,
        savedSite: AutofillUrlMatcher.ExtractedUrlParts,
    ): Boolean {
        return visitedSite.subdomain.equals(savedSite.subdomain, ignoreCase = true)
    }

    private fun specialHandlingForWwwSubdomainOnSavedSite(
        visitedSite: AutofillUrlMatcher.ExtractedUrlParts,
        savedSite: AutofillUrlMatcher.ExtractedUrlParts,
    ): Boolean {
        return (visitedSite.subdomain == null && savedSite.subdomain.equals(WWW, ignoreCase = true))
    }

    private fun savedSiteHasNoSubdomain(savedSite: AutofillUrlMatcher.ExtractedUrlParts): Boolean {
        return savedSite.subdomain == null
    }

    private fun String.normalizeScheme(): String {
        if (!startsWith("https://") && !startsWith("http://")) {
            return "https://$this"
        }
        return this
    }

    companion object {
        private const val WWW = "www"
    }
}
