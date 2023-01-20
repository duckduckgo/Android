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

package com.duckduckgo.autofill.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutofillDomainFormatterDomainNameOnlyTest {
    private val testee = AutofillDomainFormatterDomainNameOnly()

    @Test
    fun whenSimpleDomainThenNoChanges() {
        val result = testee.extractDomain("example.com")
        assertEquals("example.com", result)
    }

    @Test
    fun whenDomainStartsHttpThenSchemaStripped() {
        val result = testee.extractDomain("http://example.com")
        assertEquals("example.com", result)
    }

    @Test
    fun whenDomainStartsHttpsThenSchemaStripped() {
        val result = testee.extractDomain("https://example.com")
        assertEquals("example.com", result)
    }

    @Test
    fun whenDomainContainsWwwSubdomainThenSubdomainStripped() {
        val result = testee.extractDomain("www.example.com")
        assertEquals("example.com", result)
    }

    @Test
    fun whenDomainContainsWwwSubdomainAndHttpSchemaThenBothStripped() {
        val result = testee.extractDomain("http://www.example.com")
        assertEquals("example.com", result)
    }

    @Test
    fun whenDomainContainsWwwSubdomainAndHttpsSchemaThenBothStripped() {
        val result = testee.extractDomain("https://www.example.com")
        assertEquals("example.com", result)
    }

    @Test
    fun whenDomainContainsSubdomainThatIsNotWwwThenSubdomainNotStripped() {
        val result = testee.extractDomain("login.example.com")
        assertEquals("login.example.com", result)
    }

    @Test
    fun whenDomainIsNullThenThenFormattedAsNull() {
        val result = testee.extractDomain(null)
        assertNull(result)
    }

    @Test
    fun whenDomainIsEmptyStringThenThenFormattedAsNull() {
        val result = testee.extractDomain("")
        assertNull(result)
    }

    @Test
    fun whenDomainIsBlankStringThenThenFormattedAsNull() {
        val result = testee.extractDomain("  ")
        assertNull(result)
    }
}
