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
import com.duckduckgo.autofill.impl.deduper.AutofillDeduplicationMatchTypeDetector.MatchType
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutofillDeduplicationMatchTypeDetector {

    fun detectMatchType(
        originalUrl: String,
        login: LoginCredentials,
    ): MatchType

    sealed interface MatchType {
        data object PerfectMatch : MatchType
        data object PartialMatch : MatchType
        data object NotAMatch : MatchType
    }
}

@ContributesBinding(AppScope::class)
class RealAutofillDeduplicationMatchTypeDetector @Inject constructor(
    private val urlMatcher: AutofillUrlMatcher,
) : AutofillDeduplicationMatchTypeDetector {

    override fun detectMatchType(
        originalUrl: String,
        login: LoginCredentials,
    ): MatchType {
        val visitedSiteParts = urlMatcher.extractUrlPartsForAutofill(originalUrl)
        val savedSiteParts = urlMatcher.extractUrlPartsForAutofill(login.domain)

        if (!urlMatcher.matchingForAutofill(visitedSiteParts, savedSiteParts)) {
            return MatchType.NotAMatch
        }

        return if (visitedSiteParts.subdomain == savedSiteParts.subdomain) {
            MatchType.PerfectMatch
        } else {
            MatchType.PartialMatch
        }
    }
}
