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

package com.duckduckgo.autofill.impl.ui.credential.selecting

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialListSorter
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsGrouper.Groups
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.util.*
import javax.inject.Inject
import javax.inject.Named
import kotlin.Comparator

interface AutofillSelectCredentialsGrouper {
    fun group(
        originalUrl: String,
        unsortedCredentials: List<LoginCredentials>,
    ): Groups

    data class Groups(
        val perfectMatches: List<LoginCredentials>,
        val partialMatches: Map<String, List<LoginCredentials>>,
        val shareableCredentials: Map<String, List<LoginCredentials>>,
    )
}

@ContributesBinding(AppScope::class)
class RealAutofillSelectCredentialsGrouper @Inject constructor(
    private val autofillUrlMatcher: AutofillUrlMatcher,
    private val sorter: CredentialListSorter,
    @Named("LastUsedCredentialSorter") private val lastUsedCredentialSorter: TimestampBasedLoginSorter,
    @Named("LastUpdatedCredentialSorter") private val lastUpdatedCredentialSorter: TimestampBasedLoginSorter,
) : AutofillSelectCredentialsGrouper {

    override fun group(
        originalUrl: String,
        unsortedCredentials: List<LoginCredentials>,
    ): Groups {
        // build the groups: one group for perfect matches, then each partial match can be its own group, then any shareable credentials
        val unsortedGroups = buildGroups(originalUrl, unsortedCredentials)

        // sort the partial match groups, by usual domain sorting rules
        return sort(unsortedGroups)
    }

    private fun buildGroups(
        originalUrl: String,
        unsortedCredentials: List<LoginCredentials>,
    ): Groups {
        val perfectMatches = mutableListOf<LoginCredentials>()
        val partialMatchGroups = mutableMapOf<String, MutableList<LoginCredentials>>()
        val otherGroups = mutableMapOf<String, MutableList<LoginCredentials>>()
        val visitedSiteParts = autofillUrlMatcher.extractUrlPartsForAutofill(originalUrl)

        unsortedCredentials.forEach { savedCredential ->
            val savedSiteParts = autofillUrlMatcher.extractUrlPartsForAutofill(savedCredential.domain)
            if (!autofillUrlMatcher.matchingForAutofill(visitedSiteParts, savedSiteParts)) {
                otherGroups.getOrPut(savedCredential.domain.toString()) { mutableListOf() }.add(savedCredential)
                return@forEach
            }

            if (visitedSiteParts.subdomain == savedSiteParts.subdomain) {
                perfectMatches.add(savedCredential)
            } else {
                partialMatchGroups.getOrPut(savedCredential.domain.toString()) { mutableListOf() }.add(savedCredential)
            }
        }
        return Groups(perfectMatches, partialMatchGroups, otherGroups)
    }

    private fun sort(groups: Groups): Groups {
        // sort group headings for all the partial matches using usual domain sorting rules
        val sortedPartialMatches = groups.partialMatches.toSortedMap(sorter.comparator())
        val sortedOtherMatches = groups.shareableCredentials.toSortedMap(sorter.comparator())

        // now that headings are sorted, sort inside each group, where the sort order is:
        //    last used is most important,
        //    then last modified.
        //    greater timestamps come first in the sorted list

        val sortedPerfectMatches = sortPerfectMatches(groups)
        sortImperfectMatches(sortedPartialMatches)
        sortImperfectMatches(sortedOtherMatches)

        return Groups(sortedPerfectMatches, sortedPartialMatches, sortedOtherMatches)
    }

    private fun sortPerfectMatches(groups: Groups): List<LoginCredentials> {
        return groups.perfectMatches.sortedWith(timestampComparator())
    }

    private fun sortImperfectMatches(group: SortedMap<String, List<LoginCredentials>>) {
        group.forEach { (key, value) ->
            val sorted = value.sortedWith(timestampComparator())
            group[key] = sorted
        }
    }

    private fun timestampComparator(): Comparator<LoginCredentials> {
        return lastUsedCredentialSorter.reversed().then(lastUpdatedCredentialSorter.reversed())
    }
}
