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

package com.duckduckgo.autofill.impl.ui.credential.management.sorting

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher.ExtractedUrlParts
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.text.Collator
import javax.inject.Inject

interface CredentialListSorter {
    fun sort(credentials: List<LoginCredentials>): List<LoginCredentials>
    fun comparator(): Collator
}

@ContributesBinding(AppScope::class)
class CredentialListSorterByTitleAndDomain @Inject constructor(
    private val autofillUrlMatcher: AutofillUrlMatcher,
) : CredentialListSorter {

    override fun sort(credentials: List<LoginCredentials>): List<LoginCredentials> {
        return credentials.sortedWith(credentialComparator)
    }

    override fun comparator(): Collator {
        return buildCollator()
    }

    private val credentialComparator = object : Comparator<LoginCredentials> {

        /**
         * Sort by title if present, then by domain, then by subdomain
         */
        override fun compare(
            o1: LoginCredentials?,
            o2: LoginCredentials?,
        ): Int {
            if (o1 == null && o2 == null) return 0
            if (o1 == null) return -1
            if (o2 == null) return 1

            val titles = extractTitles(o1, o2)
            val rawDomains = extractRawDomains(o1, o2)
            val domainParts = extractDomainParts(rawDomains)
            val bestMatches = extractBestMatches(titles, rawDomains, domainParts)

            with(buildCollator()) {
                val comparison = compareFields(bestMatches.first, bestMatches.second)
                if (comparison != 0) {
                    return comparison
                }

                // couldn't sort based on title or e-tld+1, so need to look at subdomain
                return compareFields(domainParts.first.subdomain, domainParts.second.subdomain)
            }
        }
    }

    private fun extractTitles(
        o1: LoginCredentials,
        o2: LoginCredentials,
    ): Pair<String?, String?> {
        return Pair(o1.domainTitle?.uppercase(), o2.domainTitle?.uppercase())
    }

    private fun extractRawDomains(
        o1: LoginCredentials,
        o2: LoginCredentials,
    ): Pair<String?, String?> {
        return Pair(o1.domain?.extractDomain(), o2.domain?.extractDomain())
    }

    private fun extractDomainParts(rawDomains: Pair<String?, String?>): Pair<ExtractedUrlParts, ExtractedUrlParts> {
        val domainParts1 = autofillUrlMatcher.extractUrlPartsForAutofill(rawDomains.first)
        val domainParts2 = autofillUrlMatcher.extractUrlPartsForAutofill(rawDomains.second)
        return Pair(domainParts1, domainParts2)
    }

    private fun extractBestMatches(
        titles: Pair<String?, String?>,
        rawDomains: Pair<String?, String?>,
        domainParts: Pair<ExtractedUrlParts, ExtractedUrlParts>,
    ): Pair<String?, String?> {
        val identicalTitles = titles.first == titles.second
        val bestMatch1 = getBestPrimarySortField(titles.first, rawDomains.first, domainParts.first, identicalTitles)
        val bestMatch2 = getBestPrimarySortField(titles.second, rawDomains.second, domainParts.second, identicalTitles)
        return Pair(bestMatch1, bestMatch2)
    }

    /**
     * Get the best candidate to sort on between the title and the user-facing e-tld+1.
     * If a title is present and different from the other title being compared, then title is the best thing to compare.
     * If both titles being compared are identical, or title is missing, then we want to use the e-tld+1.
     *
     * If we can't use title, and we can't use e-tld+1 because it's null, then offer back the raw domain.
     */
    private fun getBestPrimarySortField(
        title: String?,
        rawDomain: String?,
        domainParts: ExtractedUrlParts,
        identicalTitles: Boolean,
    ): String? {
        if (title != null && !identicalTitles) {
            return title
        }

        domainParts.userFacingETldPlus1?.let { return it }

        return rawDomain
    }

    private fun Collator.compareFields(
        field1: String?,
        field2: String?,
    ): Int {
        if (field1 == null && field2 == null) return 0
        if (field1 == null) return -1
        if (field2 == null) return 1
        return getCollationKey(field1).compareTo(getCollationKey(field2))
    }

    fun buildCollator(): Collator {
        val coll: Collator = Collator.getInstance()
        coll.strength = Collator.SECONDARY
        return coll
    }
}
