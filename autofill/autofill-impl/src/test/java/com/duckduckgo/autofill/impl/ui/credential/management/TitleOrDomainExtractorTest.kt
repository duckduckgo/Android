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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.store.urlmatcher.AutofillDomainNameUrlMatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TitleOrDomainExtractorTest {

    private val domainFormatter: AutofillUrlMatcher = AutofillDomainNameUrlMatcher()
    private val testee = TitleOrDomainExtractor(domainFormatter)

    @Test
    fun whenTitleIsNullAndNoSubdomainThenDomainIsUsed() {
        val result = testee.extract(creds(title = null, domain = "example.com"))
        assertEquals("example.com", result)
    }

    @Test
    fun whenTitleIsNullAndDomainHasSubdomainThenCombinedSubdomainAndDomainIsUsed() {
        val result = testee.extract(creds(title = null, domain = "foo.example.com"))
        assertEquals("foo.example.com", result)
    }

    @Test
    fun whenTitleIsEmptyThenDomainIsUsed() {
        val result = testee.extract(creds(title = "", domain = "example.com"))
        assertEquals("example.com", result)
    }

    @Test
    fun whenTitleIsBlankThenDomainIsUsed() {
        val result = testee.extract(creds(title = "  ", domain = "example.com"))
        assertEquals("example.com", result)
    }

    @Test
    fun whenTitleIsPopulatedThenDomainNotUsed() {
        val result = testee.extract(creds(title = "Site", domain = "example.com"))
        assertEquals("Site", result)
    }

    @Test
    fun whenPortIsNullThenPortOmitted() {
        val result = testee.extract(creds(title = null, domain = "example.com"))
        assertEquals("example.com", result)
    }

    @Test
    fun whenPortSpecifiedInCredentialThenPortIncluded() {
        val result = testee.extract(creds(title = null, domain = "example.com:9000"))
        assertEquals("example.com:9000", result)
    }

    @Test
    fun whenIsIpAddressThenIpAddressIsUsed() {
        val result = testee.extract(creds(domain = "192.168.0.1", title = null))
        assertEquals("192.168.0.1", result)
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
