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

package com.duckduckgo.autofill.impl.encoding

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UrlUnicodeNormalizerImplTest {

    private val testee = UrlUnicodeNormalizerImpl()

    @Test
    fun whenNormalizingToAsciiAndContainsNonAsciiThenOutputIdnaEncoded() {
        assertEquals("xn--7ca.com", testee.normalizeAscii("ç.com"))
    }

    @Test
    fun whenNormalizingToAsciiAndOnlyContainsAsciiThenThenInputAndOutputIdentical() {
        assertEquals("c.com", testee.normalizeAscii("c.com"))
    }

    @Test
    fun whenNormalizingToUnicodeAndContainsNonAsciiThenOutputContainsNonAscii() {
        assertEquals("ç.com", testee.normalizeUnicode("xn--7ca.com"))
    }

    @Test
    fun whenNormalizingToUnicodeAndOnlyContainsAsciiThenThenInputAndOutputIdentical() {
        assertEquals("c.com", testee.normalizeUnicode("c.com"))
    }

    @Test
    fun whenNormalizingToAsciiWithSchemesThenSchemePreserved() {
        assertEquals("http://xn--7ca.com", testee.normalizeAscii("http://ç.com"))
        assertEquals("https://xn--7ca.com", testee.normalizeAscii("https://ç.com"))
    }

    @Test
    fun whenNormalizingToAsciiWithUrlComponentsThenAllPreserved() {
        assertEquals("http://xn--7ca.com/path", testee.normalizeAscii("http://ç.com/path"))
        assertEquals("http://xn--7ca.com?query=value", testee.normalizeAscii("http://ç.com?query=value"))
        assertEquals("http://xn--7ca.com#fragment", testee.normalizeAscii("http://ç.com#fragment"))
        assertEquals("https://xn--7ca.com/deep/nested/path/file.html", testee.normalizeAscii("https://ç.com/deep/nested/path/file.html"))
        assertEquals("https://xn--7ca.com/search?q=test&lang=en&page=1", testee.normalizeAscii("https://ç.com/search?q=test&lang=en&page=1"))
    }

    @Test
    fun whenNormalizingToAsciiWithComplexUrlThenAllComponentsPreserved() {
        assertEquals("http://xn--7ca.com/path?query=value#fragment", testee.normalizeAscii("http://ç.com/path?query=value#fragment"))
    }

    @Test
    fun whenNormalizingToAsciiWithUrlContainingInvalidDomainCharactersThenProcessesCorrectly() {
        // This URL contains characters that are invalid in domain names (/,?,=,&) in the path/query
        // Old implementation: tries to pass entire path to IDNA, fails, returns original
        // New implementation: processes only hostname, succeeds, preserves path/query
        val input = "https://google.com/signin?continue=https%3A%2F%2Fpasswords.com&id=123"
        val expected = "https://google.com/signin?continue=https%3A%2F%2Fpasswords.com&id=123"
        assertEquals(expected, testee.normalizeAscii(input))
    }

    @Test
    fun whenNormalizingToAsciiWithNoSchemeThenProcessedWithoutScheme() {
        assertEquals("xn--7ca.com/path", testee.normalizeAscii("ç.com/path"))
    }

    @Test
    fun whenNormalizingToAsciiWithPortNumberThenPortPreserved() {
        assertEquals("https://xn--7ca.com:8080/path", testee.normalizeAscii("https://ç.com:8080/path"))
    }

    @Test
    fun whenNormalizingToAsciiWithSubdomainThenSubdomainProcessed() {
        assertEquals("https://xn--sb-xka.xn--dmain-jua.com", testee.normalizeAscii("https://süb.dömain.com"))
    }

    @Test
    fun whenNormalizingToAsciiWithNullInputThenReturnsNull() {
        assertNull(testee.normalizeAscii(null))
    }

    @Test
    fun whenNormalizingToUnicodeWithNullInputThenReturnsNull() {
        assertNull(testee.normalizeUnicode(null))
    }

    @Test
    fun whenNormalizingToAsciiWithEmptyStringThenReturnsEmptyString() {
        assertEquals("", testee.normalizeAscii(""))
    }

    @Test
    fun whenNormalizingToUnicodeWithEmptyStringThenReturnsEmptyString() {
        assertEquals("", testee.normalizeUnicode(""))
    }

    @Test
    fun whenNormalizingToUnicodeWithComplexUrlThenAllComponentsPreserved() {
        assertEquals("https://ç.com/path?query=value#fragment", testee.normalizeUnicode("https://xn--7ca.com/path?query=value#fragment"))
    }

    @Test
    fun whenNormalizingToUnicodeWithSchemesThenSchemePreserved() {
        assertEquals("http://ç.com", testee.normalizeUnicode("http://xn--7ca.com"))
        assertEquals("https://ç.com", testee.normalizeUnicode("https://xn--7ca.com"))
    }

    @Test
    fun whenNormalizingToAsciiWithPortThenPortIncludedInHostname() {
        // Current implementation includes port in hostname - verify this behavior
        assertEquals("https://example.com:8080/path", testee.normalizeAscii("https://example.com:8080/path"))
    }
}
