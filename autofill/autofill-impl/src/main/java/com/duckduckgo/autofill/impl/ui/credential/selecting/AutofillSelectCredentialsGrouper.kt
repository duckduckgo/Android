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
import com.duckduckgo.autofill.api.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialListSorter
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsGrouper.Groups
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutofillSelectCredentialsGrouper {
    fun group(
        originalUrl: String,
        unsortedCredentials: List<LoginCredentials>,
    ): Groups

    data class Groups(
        val perfectMatches: List<LoginCredentials>,
        val partialMatches: Map<String, List<LoginCredentials>>,
    )
}

@ContributesBinding(FragmentScope::class)
class RealAutofillSelectCredentialsGrouper @Inject constructor(
    private val autofillUrlMatcher: AutofillUrlMatcher,
    private val sorter: CredentialListSorter,
) : AutofillSelectCredentialsGrouper {

    override fun group(
        originalUrl: String,
        unsortedCredentials: List<LoginCredentials>,
    ): Groups {
        // build the groups: one group for perfect matches, then each partial match can be its own group
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
        val visitedSiteParts = autofillUrlMatcher.extractUrlPartsForAutofill(originalUrl)

        unsortedCredentials.forEach { savedCredential ->
            val savedSiteParts = autofillUrlMatcher.extractUrlPartsForAutofill(savedCredential.domain)
            if (!autofillUrlMatcher.matchingForAutofill(visitedSiteParts, savedSiteParts)) {
                return@forEach
            }

            if (visitedSiteParts.subdomain == savedSiteParts.subdomain) {
                perfectMatches.add(savedCredential)
            } else {
                partialMatchGroups.getOrPut(savedCredential.domain.toString()) { mutableListOf() }.add(savedCredential)
            }
        }
        return Groups(perfectMatches, partialMatchGroups)
    }

    private fun sort(groups: Groups): Groups {
        // sort group headings for all the partial matches using usual domain sorting rules
        val sortedPartialMatches = groups.partialMatches.toSortedMap(sorter.comparator())

        // sort inside each group, where most recently updated is first
        val sortedPerfectMatches = groups.perfectMatches.sortedByDescending { it.lastUpdatedMillis }
        sortedPartialMatches.forEach { (key, value) ->
            val sorted = value.sortedByDescending { it.lastUpdatedMillis }
            sortedPartialMatches[key] = sorted
        }

        return Groups(sortedPerfectMatches, sortedPartialMatches)
    }
}
