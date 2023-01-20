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
import com.duckduckgo.autofill.impl.AutofillDomainFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class TitleOrDomainExtractorTest {

    private val domainFormatter: AutofillDomainFormatter = mock()
    private val testee = TitleOrDomainExtractor(domainFormatter)

    @Test
    fun whenTitleIsNullThenDomainIsUsed() {
        val result = testee.extract(creds(title = null, domain = "example.com"))
        verify(domainFormatter).extractDomain("example.com")
    }

    @Test
    fun whenTitleIsEmptyThenDomainIsUsed() {
        val result = testee.extract(creds(title = "", domain = "example.com"))
        verify(domainFormatter).extractDomain("example.com")
    }

    @Test
    fun whenTitleIsBlankThenDomainIsUsed() {
        val result = testee.extract(creds(title = "  ", domain = "example.com"))
        verify(domainFormatter).extractDomain("example.com")
    }

    @Test
    fun whenTitleIsPopulatedThenDomainNotUsed() {
        val result = testee.extract(creds(title = "Site", domain = "example.com"))
        assertEquals("Site", result)
        verify(domainFormatter, never()).extractDomain("example.com")
    }

    private fun creds(title: String?, domain: String?) = LoginCredentials(
        domainTitle = title,
        domain = domain,
        username = null,
        password = null,
    )
}
