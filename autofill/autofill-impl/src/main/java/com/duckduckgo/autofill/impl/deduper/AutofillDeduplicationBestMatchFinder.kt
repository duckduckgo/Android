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

package com.duckduckgo.autofill.impl.deduper

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.deduper.AutofillDeduplicationMatchTypeDetector.MatchType.NotAMatch
import com.duckduckgo.autofill.impl.deduper.AutofillDeduplicationMatchTypeDetector.MatchType.PartialMatch
import com.duckduckgo.autofill.impl.deduper.AutofillDeduplicationMatchTypeDetector.MatchType.PerfectMatch
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutofillDeduplicationBestMatchFinder {

    fun findBestMatch(
        originalUrl: String,
        logins: List<LoginCredentials>,
    ): LoginCredentials?
}

@ContributesBinding(AppScope::class)
class RealAutofillDeduplicationBestMatchFinder @Inject constructor(
    private val matchTypeDetector: AutofillDeduplicationMatchTypeDetector,
) : AutofillDeduplicationBestMatchFinder {

    override fun findBestMatch(
        originalUrl: String,
        logins: List<LoginCredentials>,
    ): LoginCredentials? {
        // perfect matches are those where the subdomain and e-tld+1 match
        val perfectMatches = mutableListOf<LoginCredentials>()

        // partial matches are those where only e-tld+1 matches
        val partialMatches = mutableListOf<LoginCredentials>()

        // non-matches are those where neither subdomain nor e-tld+1 match
        val nonMatches = mutableListOf<LoginCredentials>()

        categoriseEachLogin(logins, originalUrl, perfectMatches, partialMatches, nonMatches)

        if (perfectMatches.isEmpty() && partialMatches.isEmpty() && nonMatches.isEmpty()) {
            return null
        }

        return if (perfectMatches.isNotEmpty()) {
            bestPerfectMatch(perfectMatches)
        } else if (partialMatches.isNotEmpty()) {
            bestPartialMatch(partialMatches)
        } else {
            bestNonMatch(nonMatches)
        }
    }

    private fun categoriseEachLogin(
        logins: List<LoginCredentials>,
        originalUrl: String,
        perfectMatches: MutableList<LoginCredentials>,
        partialMatches: MutableList<LoginCredentials>,
        nonMatches: MutableList<LoginCredentials>,
    ) {
        logins.forEach {
            when (matchTypeDetector.detectMatchType(originalUrl, it)) {
                PerfectMatch -> perfectMatches.add(it)
                PartialMatch -> partialMatches.add(it)
                NotAMatch -> nonMatches.add(it)
            }
        }
    }

    private fun bestPerfectMatch(perfectMatches: List<LoginCredentials>): LoginCredentials {
        return perfectMatches.sortedWith(AutofillDeduplicationLoginComparator()).first()
    }

    private fun bestPartialMatch(partialMatches: MutableList<LoginCredentials>): LoginCredentials {
        return partialMatches.sortedWith(AutofillDeduplicationLoginComparator()).first()
    }

    private fun bestNonMatch(nonMatches: MutableList<LoginCredentials>): LoginCredentials {
        return nonMatches.sortedWith(AutofillDeduplicationLoginComparator()).first()
    }
}
