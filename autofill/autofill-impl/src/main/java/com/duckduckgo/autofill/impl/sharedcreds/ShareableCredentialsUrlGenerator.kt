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

package com.duckduckgo.autofill.impl.sharedcreds

import com.duckduckgo.autofill.impl.sharedcreds.SharedCredentialsParser.SharedCredentialConfig
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher.ExtractedUrlParts
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface ShareableCredentialsUrlGenerator {
    fun generateShareableUrls(
        sourceUrl: String,
        config: SharedCredentialConfig,
    ): List<ExtractedUrlParts>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealShareableCredentialsUrlGenerator @Inject constructor(
    private val autofillUrlMatcher: AutofillUrlMatcher,
) : ShareableCredentialsUrlGenerator {

    override fun generateShareableUrls(
        sourceUrl: String,
        config: SharedCredentialConfig,
    ): List<ExtractedUrlParts> {
        val visitedSiteUrlParts = autofillUrlMatcher.extractUrlPartsForAutofill(sourceUrl)

        val omnidirectionalMatches = omnidirectionalShareableUrls(config, visitedSiteUrlParts)
        val unidirectionalMatches = unidirectionalShareableUrls(config, visitedSiteUrlParts)

        return (omnidirectionalMatches + unidirectionalMatches).distinct()
    }

    private fun unidirectionalShareableUrls(
        config: SharedCredentialConfig,
        visitedSiteUrlParts: ExtractedUrlParts,
    ): MutableList<ExtractedUrlParts> {
        val unidirectionalMatches = mutableListOf<ExtractedUrlParts>()
        config.unidirectionalRules.forEach { rule ->

            val matches = rule.to.filter { autofillUrlMatcher.matchingForAutofill(visitedSiteUrlParts, it) }
            matches.forEach { _ ->
                unidirectionalMatches.addAll(rule.from.removeExactMatch(visitedSiteUrlParts))
            }
        }
        return unidirectionalMatches
    }

    private fun omnidirectionalShareableUrls(
        config: SharedCredentialConfig,
        visitedSiteUrlParts: ExtractedUrlParts,
    ): MutableList<ExtractedUrlParts> {
        val omnidirectionalMatches = mutableListOf<ExtractedUrlParts>()
        config.omnidirectionalRules.forEach { rule ->

            val matches = rule.shared.filter { autofillUrlMatcher.matchingForAutofill(visitedSiteUrlParts, it) }
            matches.forEach { _ ->
                omnidirectionalMatches.addAll(rule.shared.removeExactMatch(visitedSiteUrlParts))
            }
        }
        return omnidirectionalMatches
    }

    private fun List<ExtractedUrlParts>.removeExactMatch(visitedSiteUrlParts: ExtractedUrlParts): List<ExtractedUrlParts> {
        return filterNot { it.eTldPlus1 == visitedSiteUrlParts.eTldPlus1 }
    }
}
