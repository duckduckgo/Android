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

package com.duckduckgo.autofill.impl.ui.credential.management

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.api.urlmatcher.AutofillUrlMatcher.ExtractedUrlParts
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TitleOrDomainExtractorTest {

    private val domainFormatter: AutofillUrlMatcher = mock()
    private val testee = TitleOrDomainExtractor(domainFormatter)

    @Test
    fun whenTitleIsNullAndNoSubdomainThenDomainIsUsed() {
        whenever(domainFormatter.extractUrlPartsForAutofill("example.com")).thenReturn(ExtractedUrlParts(eTldPlus1 = "example.com", subdomain = null))
        val result = testee.extract(creds(title = null, domain = "example.com"))
        assertEquals("example.com", result)
    }

    @Test
    fun whenTitleIsNullAndDomainHasSubdomainThenCombinedSubdomainAndDomainIsUsed() {
        whenever(domainFormatter.extractUrlPartsForAutofill("example.com")).thenReturn(
            ExtractedUrlParts(eTldPlus1 = "example.com", subdomain = "foo"),
        )
        val result = testee.extract(creds(title = null, domain = "example.com"))
        assertEquals("foo.example.com", result)
    }

    @Test
    fun whenTitleIsEmptyThenDomainIsUsed() {
        whenever(domainFormatter.extractUrlPartsForAutofill("example.com")).thenReturn(ExtractedUrlParts(eTldPlus1 = "example.com", subdomain = null))
        val result = testee.extract(creds(title = "", domain = "example.com"))
        assertEquals("example.com", result)
    }

    @Test
    fun whenTitleIsBlankThenDomainIsUsed() {
        whenever(domainFormatter.extractUrlPartsForAutofill("example.com")).thenReturn(ExtractedUrlParts(eTldPlus1 = "example.com", subdomain = null))
        val result = testee.extract(creds(title = "  ", domain = "example.com"))
        assertEquals("example.com", result)
    }

    @Test
    fun whenTitleIsPopulatedThenDomainNotUsed() {
        val result = testee.extract(creds(title = "Site", domain = "example.com"))
        assertEquals("Site", result)
        verify(domainFormatter, never()).extractUrlPartsForAutofill("example.com")
    }

    private fun creds(
        title: String?,
        domain: String?,
    ) = LoginCredentials(
        domainTitle = title,
        domain = domain,
        username = null,
        password = null,
    )
}
