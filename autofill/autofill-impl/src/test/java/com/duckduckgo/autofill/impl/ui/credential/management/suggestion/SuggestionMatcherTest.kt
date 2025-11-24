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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.encoding.TestUrlUnicodeNormalizer
import com.duckduckgo.autofill.impl.sharedcreds.ShareableCredentials
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SuggestionMatcherTest {

    private val shareableCredentials: ShareableCredentials = mock()

    private val testee = SuggestionMatcher(
        autofillUrlMatcher = AutofillDomainNameUrlMatcher(TestUrlUnicodeNormalizer()),
        shareableCredentials = shareableCredentials,
    )

    @Test
    fun whenUrlIsNullThenNoSuggestions() = runTest {
        configureNoShareableCredentials()
        val suggestions = testee.getDirectSuggestions(null, listOf())
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun whenUrlIsNotNullAndNoCredentialsThenNoSuggestions() = runTest {
        configureNoShareableCredentials()
        val suggestions = testee.getDirectSuggestions("https://duckduckgo.com", listOf())
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun whenCredentialsAvailableButNotAMatchThenNoSuggestions() = runTest {
        configureNoShareableCredentials()
        val creds = listOf(creds("https://example.com"))
        val suggestions = testee.getDirectSuggestions("https://duckduckgo.com", creds)
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun whenCredentialsAvailableWithASingleMatchThenOneSuggestion() = runTest {
        configureNoShareableCredentials()
        val creds = listOf(
            creds("https://example.com"),
            creds("https://duckduckgo.com"),
        )
        val suggestions = testee.getDirectSuggestions("https://duckduckgo.com", creds)
        assertEquals(1, suggestions.size)
        assertEquals("https://duckduckgo.com", suggestions.first().domain)
    }

    @Test
    fun whenCredentialsAvailableWithMultipleMatchesThenMultipleSuggestions() = runTest {
        configureNoShareableCredentials()
        val creds = listOf(
            creds("https://example.com"),
            creds("https://duckduckgo.com"),
            creds("https://example.com"),
            creds("https://duckduckgo.com/this/should/also/match"),
        )
        val suggestions = testee.getDirectSuggestions("https://duckduckgo.com", creds)
        assertEquals(2, suggestions.size)
    }

    @Test
    fun whenSubdomainIncludedInSavedSiteAndVisitingRootSiteThenSuggestionOffered() = runTest {
        configureNoShareableCredentials()
        val creds = listOf(creds("https://duckduckgo.com"))
        val suggestions = testee.getDirectSuggestions("https://test.duckduckgo.com", creds)
        assertEquals(1, suggestions.size)
    }

    @Test
    fun whenPortIncludedInSavedSiteAndNotInVisitedSiteThenNotASuggestion() = runTest {
        configureNoShareableCredentials()
        val creds = listOf(creds("example.com:8080"))
        val suggestions = testee.getDirectSuggestions("example.com", creds)
        assertEquals(0, suggestions.size)
    }

    @Test
    fun whenPortIncludedInVisitedSiteAndNotInSavedSiteThenNotASuggestion() = runTest {
        configureNoShareableCredentials()
        val creds = listOf(creds("example.com"))
        val suggestions = testee.getDirectSuggestions("example.com:8080", creds)
        assertEquals(0, suggestions.size)
    }

    @Test
    fun whenPortIncludedInVisitedSiteDiffersFromPortInSavedSiteThenNotASuggestion() = runTest {
        configureNoShareableCredentials()
        val creds = listOf(creds("example.com:9000"))
        val suggestions = testee.getDirectSuggestions("example.com:8080", creds)
        assertEquals(0, suggestions.size)
    }

    @Test
    fun whenPortIncludedInVisitedSiteMatchesPortInSavedSiteThenNotASuggestion() = runTest {
        configureNoShareableCredentials()
        val creds = listOf(creds("example.com:9000"))
        val suggestions = testee.getDirectSuggestions("example.com:9000", creds)
        assertEquals(1, suggestions.size)
    }

    @Test
    fun whenQueryIsNotUrlButMatchesSuggestionsDomainThenSuggestionsOffered() = runTest {
        configureNoShareableCredentials()
        val creds = listOf(creds("example.com"))
        val suggestions = testee.getQuerySuggestions("example", creds)
        assertEquals(1, suggestions.size)
    }

    private suspend fun configureNoShareableCredentials() {
        whenever(shareableCredentials.shareableCredentials(any())).thenReturn(emptyList())
    }

    private fun creds(domain: String): LoginCredentials {
        return LoginCredentials(id = 0, domain = domain, username = "username", password = "password")
    }
}
