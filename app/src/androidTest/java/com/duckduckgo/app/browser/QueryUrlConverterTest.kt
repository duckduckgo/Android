/*
 * Copyright (c) 2017 DuckDuckGo
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

import android.net.Uri
import android.support.test.runner.AndroidJUnit4
import com.duckduckgo.app.browser.omnibar.QueryUrlConverter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueryUrlConverterTest {


    val testee: QueryUrlConverter = QueryUrlConverter(DuckDuckGoRequestRewriter())

    @Test
    fun whenUserIsPresentThenIsWebUrlIsFalse() {
        val input = "http://example.com@sample.com"
        assertFalse(testee.isWebUrl(input))
    }

    @Test
    fun whenGivenLongWellFormedUrlThenIsWebUrlIsTrue() {
        val input = "http://www.veganchic.com/products/Camo-High-Top-Sneaker-by-The-Critical-Slide-Societ+80758-0180.html"
        assertTrue(testee.isWebUrl(input))
    }

    @Test
    fun whenHostIsValidThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("test.com"))
    }

    @Test
    fun whenHostIsValidIpAddressThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("121.33.2.11"))
    }

    @Test
    fun whenHostIsLocalhostThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("localhost"))
    }

    @Test
    fun whenHostIsInvalidContainsSpaceThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("t est.com"))
    }

    @Test
    fun whenHostIsInvalidContainsExclamationMarkThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("test!com.com"))
    }

    @Test
    fun whenHostIsInvalidIpThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("121.33.33."))
    }

    @Test
    fun whenHostIsInvalidMisspelledLocalhostContainsSpaceThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("localhostt"))
    }

    @Test
    fun whenSchemeIsValidNormalUrlThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("http://test.com"))
    }

    @Test
    fun whenSchemeIsValidIpAddressThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("http://121.33.2.11"))
    }

    @Test
    fun whenSchemeIsValidLocalhostUrlThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("http://localhost"))
    }

    @Test
    fun whenSchemeIsInvalidNormalUrlThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("asdas://test.com"))
    }

    @Test
    fun whenSchemeIsInvalidIpAddressThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("asdas://121.33.2.11"))
    }

    @Test
    fun whenSchemeIsInvalidLocalhostThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("asdas://localhost"))
    }

    @Test
    fun whenTextIsIncompleteHttpSchemeLettersOnlyThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("http"))
    }

    @Test
    fun whenTextIsIncompleteHttpSchemeMissingBothSlashesThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("http:"))
    }

    @Test
    fun whenTextIsIncompleteHttpSchemeMissingOneSlashThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("http:/"))
    }

    @Test
    fun whenTextIsIncompleteHttpsSchemeLettersOnlyThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("https"))
    }

    @Test
    fun whenTextIsIncompleteHttpsSchemeMissingBothSlashesThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("https:"))
    }

    @Test
    fun whenTextIsIncompleteHttpsSchemeMissingOneSlashThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("https:/"))
    }

    @Test
    fun whenPathIsValidNormalUrlThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("http://test.com/path"))
    }

    @Test
    fun whenPathIsValidIpAddressThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("http://121.33.2.11/path"))
    }

    @Test
    fun whenPathIsValidLocalhostThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("http://localhost/path"))
    }

    @Test
    fun whenPathIsValidMissingSchemeNormalUrlThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("test.com/path"))
    }

    @Test
    fun whenPathIsValidMissingSchemeIpAddressThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("121.33.2.11/path"))
    }

    @Test
    fun whenPathIsValidMissingSchemeLocalhostThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("localhost/path"))
    }

    @Test
    fun whenPathIsInvalidContainsSpaceNormalUrlThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("http://test.com/pa th"))
    }

    @Test
    fun whenPathIsInvalidContainsSpaceIpAddressThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("http://121.33.2.11/pa th"))
    }

    @Test
    fun whenPathIsInvalidContainsSpaceLocalhostThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("http://localhost/pa th"))
    }

    @Test
    fun whenPathIsInvalidContainsSpaceMissingSchemeNormalUrlThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("test.com/pa th"))
    }

    @Test
    fun whenPathIsInvalidContainsSpaceMissingSchemeIpAddressThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("121.33.2.11/pa th"))
    }

    @Test
    fun whenPathIsInvalidContainsSpaceMissingSchemeLocalhostThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl("localhost/pa th"))
    }

    @Test
    fun whenParamsAreValidNormalUrlThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("http://test.com?s=dafas&d=342"))
    }

    @Test
    fun whenParamsAreValidIpAddressThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("http://121.33.2.11?s=dafas&d=342"))
    }

    @Test
    fun whenParamsAreValidLocalhostThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("http://localhost?s=dafas&d=342"))
    }

    @Test
    fun whenParamsAreValidNormalUrlMissingSchemeThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("test.com?s=dafas&d=342"))
    }

    @Test
    fun whenParamsAreValidIpAddressMissingSchemeThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("121.33.2.11?s=dafas&d=342"))
    }

    @Test
    fun whenParamsAreValidLocalhostMissingSchemeThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("localhost?s=dafas&d=342"))
    }

    @Test
    fun whenParamsAreValidContainsEncodedUriThenIsWebUrlIsTrue() {
        assertTrue(testee.isWebUrl("https://m.facebook.com/?refsrc=https%3A%2F%2Fwww.facebook.com%2F&_rdr"))
    }

    @Test
    fun whenGivenSimpleStringThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl( "randomtext"))
    }

    @Test
    fun whenGivenStringWithDotPrefixThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl(".randomtext"))
    }

    @Test
    fun whenGivenStringWithDotSuffixThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl( "randomtext."))
    }

    @Test
    fun whenGivenNumberThenIsWebUrlIsFalse() {
        assertFalse(testee.isWebUrl( "33"))
    }

    @Test
    fun whenSingleWordThenSearchQueryBuilt() {
        val input = "foo"
        val result = testee.convertQueryToUri(input)
        assertDuckDuckGoSearchQuery("foo", result)
    }

    @Test
    fun whenWebUrlCalledWithInvalidURLThenEncodedSearchQueryBuilt() {
        val input = "http://test .com"
        val expected = "http%3A%2F%2Ftest%20.com"
        val result = testee.convertQueryToUri(input)
        assertDuckDuckGoSearchQuery(expected, result)
    }

    @Test
    fun whenEncodingQueryWithSymbolsThenQueryProperlyEncoded() {
        val input = "test \"%-.<>\\^_`{|~"
        val expected = "test%20%22%25-.%3C%3E%5C%5E_%60%7B%7C~"
        val result = testee.convertQueryToUri(input)
        assertDuckDuckGoSearchQuery(expected, result)
    }

    @Test
    fun whenParamHasInvalidCharactersThenAddingParamAppendsEncodedVersion() {
        val input = "43 + 5"
        val expected = "43%20%2B%205"
        val result = testee.convertQueryToUri(input)
        assertDuckDuckGoSearchQuery(expected, result)
    }

    @Test
    fun whenIsWebUrlMissingSchemeThenHttpWillBeAddedUponConversion() {
        val input = "example.com"
        val expected = "http://$input"
        val result = testee.convertUri(input)
        assertEquals(expected, result)
    }

    private fun assertDuckDuckGoSearchQuery(query: String, uri: Uri) {
        assertEquals("duckduckgo.com", uri.host)
        assertEquals("https", uri.scheme)
        assertEquals("", uri.path)
        assertEquals("ddg_android", uri.getQueryParameter("t"))
        assertTrue("Query string doesn't match. Expected `q=$query` somewhere in query ${uri.encodedQuery}", uri.encodedQuery.contains("q=$query"))

        val version = BuildConfig.VERSION_NAME.replace(".", "_")
        assertEquals("android_$version", uri.getQueryParameter("tappv"))
    }
}