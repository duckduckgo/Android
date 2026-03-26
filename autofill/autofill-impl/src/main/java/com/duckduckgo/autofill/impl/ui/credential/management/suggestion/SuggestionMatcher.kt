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

package com.duckduckgo.autofill.impl.ui.credential.management.suggestion

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.sharedcreds.ShareableCredentials
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import javax.inject.Inject

class SuggestionMatcher @Inject constructor(
    private val autofillUrlMatcher: AutofillUrlMatcher,
    private val shareableCredentials: ShareableCredentials,
) {

    /**
     * Returns a list of credentials that are a direct match for the current URL.
     * By direct, this means it does not consider sharable credentials. @see [getShareableSuggestions] for those.
     */
    fun getDirectSuggestions(
        currentUrl: String?,
        credentials: List<LoginCredentials>,
    ): List<LoginCredentials> {
        if (currentUrl == null) return emptyList()
        val currentSite = autofillUrlMatcher.extractUrlPartsForAutofill(currentUrl)
        if (currentSite.eTldPlus1 == null) return emptyList()

        return credentials.filter {
            val storedDomain = it.domain ?: return@filter false
            val savedSite = autofillUrlMatcher.extractUrlPartsForAutofill(storedDomain)
            return@filter autofillUrlMatcher.matchingForAutofill(currentSite, savedSite)
        }
    }

    fun getQuerySuggestions(
        query: String?,
        credentials: List<LoginCredentials>,
    ): List<LoginCredentials> {
        if (query.isNullOrBlank()) return emptyList()
        return credentials.filter { it.domain?.contains(query, ignoreCase = true) == true }
    }

    /**
     * Returns a list of credentials that are not a direct match for the current URL, but are considered shareable.
     */
    suspend fun getShareableSuggestions(currentUrl: String?): List<LoginCredentials> {
        if (currentUrl == null) return emptyList()
        return shareableCredentials.shareableCredentials(currentUrl)
    }
}
