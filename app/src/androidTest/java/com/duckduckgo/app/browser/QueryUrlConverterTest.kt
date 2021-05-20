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
import com.duckduckgo.app.browser.omnibar.QueryOrigin
import com.duckduckgo.app.browser.omnibar.QueryUrlConverter
import com.duckduckgo.app.referral.AppReferrerDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QueryUrlConverterTest {

    private var mockStatisticsStore: StatisticsDataStore = mock()
    private val variantManager: VariantManager = mock()
    private val mockAppReferrerDataStore: AppReferrerDataStore = mock()
    private val requestRewriter = DuckDuckGoRequestRewriter(DuckDuckGoUrlDetector(), mockStatisticsStore, variantManager, mockAppReferrerDataStore)
    private val testee: QueryUrlConverter = QueryUrlConverter(requestRewriter)

    @Before
    fun setup() {
        whenever(variantManager.getVariant(any())).thenReturn(VariantManager.DEFAULT_VARIANT)
    }

    @Test
    fun whenSingleWordThenSearchQueryBuilt() {
        val input = "foo"
        val result = testee.convertQueryToUrl(input)
        assertDuckDuckGoSearchQuery("foo", result)
    }

    @Test
    fun whenWebUrlCalledWithInvalidURLThenEncodedSearchQueryBuilt() {
        val input = "http://test .com"
        val expected = "http%3A%2F%2Ftest%20.com"
        val result = testee.convertQueryToUrl(input)
        assertDuckDuckGoSearchQuery(expected, result)
    }

    @Test
    fun whenEncodingQueryWithSymbolsThenQueryProperlyEncoded() {
        val input = "test \"%-.<>\\^_`{|~"
        val expected = "test%20%22%25-.%3C%3E%5C%5E_%60%7B%7C~"
        val result = testee.convertQueryToUrl(input)
        assertDuckDuckGoSearchQuery(expected, result)
    }

    @Test
    fun whenParamHasInvalidCharactersThenAddingParamAppendsEncodedVersion() {
        val input = "43 + 5"
        val expected = "43%20%2B%205"
        val result = testee.convertQueryToUrl(input)
        assertDuckDuckGoSearchQuery(expected, result)
    }

    @Test
    fun whenIsWebUrlMissingSchemeThenHttpWillBeAddedUponConversion() {
        val input = "example.com"
        val expected = "http://$input"
        val result = testee.convertQueryToUrl(input)
        assertEquals(expected, result)
    }

    @Test
    fun whenQueryOriginIsFromUserAndIsQueryThenSearchQueryBuilt() {
        val input = "foo"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser)
        assertDuckDuckGoSearchQuery("foo", result)
    }

    @Test
    fun whenQueryOriginIsFromUserAndIsUrlThenUrlReturned() {
        val input = "http://example.com"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser)
        assertEquals(input, result)
    }

    @Test
    fun whenQueryOriginIsFromAutocompleteAndNavIsFalseThenSearchQueryBuilt() {
        val input = "example.com"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromAutocomplete(nav = false))
        assertDuckDuckGoSearchQuery("example.com", result)
    }

    @Test
    fun whenQueryOriginIsFromAutocompleteAndNavIsTrueThenUrlReturned() {
        val input = "http://example.com"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromAutocomplete(nav = true))
        assertEquals(input, result)
    }

    @Test
    fun whenQueryOriginIsFromAutocompleteAndNavIsNullAndIsUrlThenUrlReturned() {
        val input = "http://example.com"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromAutocomplete(nav = null))
        assertEquals(input, result)
    }

    @Test
    fun whenQueryOriginIsFromAutocompleteAndNavIsNullAndIsNotUrlThenSearchQueryBuilt() {
        val input = "foo"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromAutocomplete(nav = null))
        assertDuckDuckGoSearchQuery("foo", result)
    }

    private fun assertDuckDuckGoSearchQuery(query: String, url: String) {
        val uri = Uri.parse(url)
        assertEquals("duckduckgo.com", uri.host)
        assertEquals("https", uri.scheme)
        assertEquals("", uri.path)
        assertEquals("ddg_android", uri.getQueryParameter("t"))
        val encodedQuery = uri.encodedQuery
        assertNotNull(encodedQuery)
        assertTrue("Query string doesn't match. Expected `q=$query` somewhere in query ${uri.encodedQuery}", encodedQuery!!.contains("q=$query"))
    }
}
