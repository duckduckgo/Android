/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser

import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SpecialUrlDetectorImplTest {

    lateinit var testee: SpecialUrlDetector

    @Before
    fun setup() {
        testee = SpecialUrlDetectorImpl()
    }

    @Test
    fun whenUrlIsHttpThenWebTypeDetected() {
        val expected = Web::class
        val actual = testee.determineType("http://example.com")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsHttpThenWebAddressInData() {
        val type: Web = testee.determineType("http://example.com") as Web
        assertEquals("http://example.com", type.webAddress)
    }

    @Test
    fun whenUrlIsHttpsThenWebTypeDetected() {
        val expected = Web::class
        val actual = testee.determineType("https://example.com")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsHttpsThenSchemePreserved() {
        val type = testee.determineType("https://example.com") as Web
        assertEquals("https://example.com", type.webAddress)
    }

    @Test
    fun whenUrlIsTelWithDashesThenTelephoneTypeDetected() {
        val expected = Telephone::class
        val actual = testee.determineType("tel:+123-555-12323")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsTelThenTelephoneTypeDetected() {
        val expected = Telephone::class
        val actual = testee.determineType("tel:12355512323")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsTelThenSchemeRemoved() {
        val type = testee.determineType("tel:+123-555-12323") as Telephone
        assertEquals("+123-555-12323", type.telephoneNumber)
    }

    @Test
    fun whenUrlIsTelpromptThenTelephoneTypeDetected() {
        val expected = Telephone::class
        val actual = testee.determineType("telprompt:12355512323")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsTelpromptThenSchemeRemoved() {
        val type = testee.determineType("telprompt:123-555-12323") as Telephone
        assertEquals("123-555-12323", type.telephoneNumber)
    }

    @Test
    fun whenUrlIsMailtoThenEmailTypeDetected() {
        val expected = Email::class
        val actual = testee.determineType("mailto:foo@example.com")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsMailtoThenSchemePreserved() {
        val type = testee.determineType("mailto:foo@example.com") as Email
        assertEquals("mailto:foo@example.com", type.emailAddress)
    }

    @Test
    fun whenUrlIsSmsThenSmsTypeDetected() {
        val expected = Sms::class
        val actual = testee.determineType("sms:123-555-13245")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsSmsToThenSmsTypeDetected() {
        val expected = Sms::class
        val actual = testee.determineType("smsto:123-555-13245")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsSmsThenSchemeRemoved() {
        val type = testee.determineType("sms:123-555-12323") as Sms
        assertEquals("123-555-12323", type.telephoneNumber)
    }

    @Test
    fun whenUrlIsSmsToThenSchemeRemoved() {
        val type = testee.determineType("smsto:123-555-12323") as Sms
        assertEquals("123-555-12323", type.telephoneNumber)
    }

    @Test
    fun whenUrlIsCustomUriSchemeThenIntentTypeDetected() {
        val type = testee.determineType("myapp:foo bar") as IntentType
        assertEquals("myapp:foo bar", type.url)
    }

    @Test
    fun whenUrlIsParametrizedQueryThenSearchQueryTypeDetected() {
        val type = testee.determineType("foo site:duckduckgo.com") as SearchQuery
        assertEquals("foo site:duckduckgo.com", type.query)
    }

    @Test
    fun whenUrlIsJavascriptSchemeThenWebSearchTypeDetected() {
        val expected = SearchQuery::class
        val actual = testee.determineType("javascript:alert(0)")
        assertEquals(expected, actual::class)
    }

    @Test
    fun whenUrlIsJavascriptSchemeThenFullQueryRetained() {
        val type = testee.determineType("javascript:alert(0)") as SearchQuery
        assertEquals("javascript:alert(0)", type.query)
    }
}