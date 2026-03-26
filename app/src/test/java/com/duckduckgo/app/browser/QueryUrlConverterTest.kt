/*
 * Copyright (c) 2021 DuckDuckGo
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

import android.annotation.SuppressLint
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.omnibar.QueryOrigin
import com.duckduckgo.app.browser.omnibar.QueryUrlConverter
import com.duckduckgo.app.browser.omnibar.QueryUrlPredictor
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.referral.AppReferrerDataStore
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.settings.api.SerpSettingsFeature
import com.duckduckgo.urlpredictor.Decision
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@SuppressLint("DenyListedApi") // fake toggle store
class QueryUrlConverterTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private var mockStatisticsStore: StatisticsDataStore = mock()
    private val variantManager: VariantManager = mock()
    private val mockAppReferrerDataStore: AppReferrerDataStore = mock()
    private val duckChat: DuckChat = mock()
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val serpSettingsFeature: SerpSettingsFeature = FakeFeatureToggleFactory.create(SerpSettingsFeature::class.java)
    private val queryUrlPredictor: QueryUrlPredictor = mock()
    private val requestRewriter =
        DuckDuckGoRequestRewriter(
            DuckDuckGoUrlDetectorImpl(),
            mockStatisticsStore,
            variantManager,
            mockAppReferrerDataStore,
            duckChat,
            androidBrowserConfigFeature,
            serpSettingsFeature,
        )
    private val testee: QueryUrlConverter = createTestee(useUrlPredictorEnabled = false)

    @Before
    fun setup() {
        whenever(variantManager.getVariantKey()).thenReturn("")
        whenever(duckChat.isEnabled()).thenReturn(true)
        whenever(queryUrlPredictor.isReady()).thenReturn(true)
        androidBrowserConfigFeature.hideDuckAiInSerpKillSwitch().setRawStoredState(State(true))
    }

    @Test
    fun `when single word then search query built`() {
        val input = "foo"
        val result = testee.convertQueryToUrl(input)
        assertDuckDuckGoSearchQuery("foo", result)
    }

    @Test
    fun `when url predictor enabled and single word then search query built`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify("foo")).thenReturn(Decision.Search("foo"))
        val input = "foo"
        val result = testee.convertQueryToUrl(input)
        assertDuckDuckGoSearchQuery("foo", result)
    }

    @Test
    fun `when web url called with invalid url then encoded search query built`() {
        val input = "http://test .com"
        val expected = "http%3A%2F%2Ftest%20.com"
        val result = testee.convertQueryToUrl(input)
        assertDuckDuckGoSearchQuery(expected, result)
    }

    @Test
    fun `when encoding query with symbols then query properly encoded`() {
        val input = "test \"%-.<>\\^_`{|~"
        val expected = "test%20%22%25-.%3C%3E%5C%5E_%60%7B%7C~"
        val result = testee.convertQueryToUrl(input)
        assertDuckDuckGoSearchQuery(expected, result)
    }

    @Test
    fun `when param has invalid characters then adding param appends encoded version`() {
        val input = "43 + 5"
        val expected = "43%20%2B%205"
        val result = testee.convertQueryToUrl(input)
        assertDuckDuckGoSearchQuery(expected, result)
    }

    @Test
    fun `when is web url missing scheme then http will be added upon conversion`() {
        val input = "example.com"
        val expected = "http://$input"
        val result = testee.convertQueryToUrl(input)
        assertEquals(expected, result)
    }

    @Test
    fun `when query origin is from user and is query then search query built`() {
        val input = "foo"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser)
        assertDuckDuckGoSearchQuery("foo", result)
    }

    @Test
    fun `when query origin is from user and is url then url returned`() {
        val input = "http://example.com"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser)
        assertEquals(input, result)
    }

    @Test
    fun `when query origin is from bookmark and is query then search query built`() {
        val input = "foo"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromBookmark)
        assertDuckDuckGoSearchQuery("foo", result)
    }

    @Test
    fun `when url predictor enabled and query origin is from bookmark and is query then search query built`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify("foo")).thenReturn(Decision.Search("foo"))
        val input = "foo"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromBookmark)
        assertDuckDuckGoSearchQuery("foo", result)
    }

    @Test
    fun `when query origin is from bookmark and is url then url returned`() {
        val input = "http://example.com"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromBookmark)
        assertEquals(input, result)
    }

    @Test
    fun `when url predictor enabled and query origin is from bookmark and is url then url returned`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify("http://example.com")).thenReturn(Decision.Navigate("http://example.com"))
        val input = "http://example.com"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromBookmark)
        assertEquals(input, result)
    }

    @Test
    fun `when query origin is from autocomplete and is nav is false then search query built`() {
        val input = "example.com"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromAutocomplete(isNav = false))
        assertDuckDuckGoSearchQuery("example.com", result)
    }

    @Test
    fun `when query origin is from autocomplete and is nav is true then url returned`() {
        val input = "http://example.com"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromAutocomplete(isNav = true))
        assertEquals(input, result)
    }

    @Test
    fun `when query origin is from autocomplete and is nav is null and is not url then search query built`() {
        val input = "foo"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromAutocomplete(isNav = null))
        assertDuckDuckGoSearchQuery("foo", result)
    }

    @Test
    fun `when duck uri then url returned`() {
        val input = "duck://settings"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser)
        assertEquals(input, result)
    }

    @Test
    fun `when url predictor enabled and duck uri then url returned`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        val input = "duck://settings"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser)
        assertEquals(input, result)
    }

    @Test
    fun `when data url then url returned`() {
        val input = "data:text/html,<h1>Test</h1>"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser)
        assertEquals(input, result)
    }

    @Test
    fun `when url predictor enabled and data url then url returned`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        val input = "data:text/html,<h1>Test</h1>"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser)
        assertEquals(input, result)
    }

    @Test
    fun `when asset url then url returned`() {
        val input = "file:///android_asset/test.html"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser)
        assertEquals(input, result)
    }

    @Test
    fun `when url predictor enabled and asset url then url returned`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        val input = "file:///android_asset/test.html"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser)
        assertEquals(input, result)
    }

    @Test
    fun `when convert query to url contains a major vertical then vertical added to url`() {
        val input = "foo"
        val vertical = QueryUrlConverter.majorVerticals.random()
        val result = testee.convertQueryToUrl(input, vertical = vertical, queryOrigin = QueryOrigin.FromUser)
        assertTrue(result.contains("iar=$vertical"))
    }

    @Test
    fun `when url predictor enabled and convert query to url contains a major vertical then vertical added to url`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify("foo")).thenReturn(Decision.Search("foo"))
        val input = "foo"
        val vertical = QueryUrlConverter.majorVerticals.random()
        val result = testee.convertQueryToUrl(input, vertical = vertical, queryOrigin = QueryOrigin.FromUser)
        assertTrue(result.contains("iar=$vertical"))
    }

    @Test
    fun `when convert query to url contains a non major vertical then vertical not added to url`() {
        val input = "foo"
        val vertical = "nonMajor"
        val result = testee.convertQueryToUrl(input, vertical = vertical, queryOrigin = QueryOrigin.FromUser)
        assertFalse(result.contains("iar=$vertical"))
    }

    @Test
    fun `when url predictor enabled and convert query to url contains a non major vertical then vertical not added to url`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify("foo")).thenReturn(Decision.Search("foo"))
        val input = "foo"
        val vertical = "nonMajor"
        val result = testee.convertQueryToUrl(input, vertical = vertical, queryOrigin = QueryOrigin.FromUser)
        assertFalse(result.contains("iar=$vertical"))
    }

    @Test
    fun `when query contains single url then url is extracted`() {
        val input = "Source: Towards Data Science  https://search.app/2W427"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertEquals("https://search.app/2W427", result)
    }

    @Test
    fun `when url predictor enabled and query contains single url then url is extracted`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify(any())).thenReturn(Decision.Search("foo"))
        whenever(queryUrlPredictor.classify("https://search.app/2W427")).thenReturn(Decision.Navigate("https://search.app/2W427"))
        val input = "Source: Towards Data Science  https://search.app/2W427"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertEquals("https://search.app/2W427", result)
    }

    @Test
    fun `when query contains multiple urls then first url starting with http is extracted`() {
        val input = "Source: MTBS.cz  https://search.app/3Uq79"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertEquals("https://search.app/3Uq79", result)
    }

    @Test
    fun `when url predictor enabled and query contains multiple urls then first url starting with http is extracted`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify(any())).thenReturn(Decision.Search("foo"))
        whenever(queryUrlPredictor.classify("https://search.app/3Uq79")).thenReturn(Decision.Navigate("https://search.app/3Uq79"))
        val input = "Source: MTBS.cz  https://search.app/3Uq79"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertEquals("https://search.app/3Uq79", result)
    }

    @Test
    fun `when query contains single url and apostrophe then url is extracted`() {
        val input = "Source: Tom's Guide https://search.app/ddbWi"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertEquals("https://search.app/ddbWi", result)
    }

    @Test
    fun `when url predictor enabled and query contains single url and apostrophe then url is extracted`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify(any())).thenReturn(Decision.Search("foo"))
        whenever(queryUrlPredictor.classify("https://search.app/ddbWi")).thenReturn(Decision.Navigate("https://search.app/ddbWi"))
        val input = "Source: Tom's Guide https://search.app/ddbWi"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertEquals("https://search.app/ddbWi", result)
    }

    @Test
    fun `when query contains single url and double quotes then url is extracted`() {
        val input =
            """
            Source: "Guide" https://search.app/ddbWi
            """.trimIndent()
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertEquals("https://search.app/ddbWi", result)
    }

    @Test
    fun `when url predictor enabled and query contains single url and double quotes then url is extracted`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify(any())).thenReturn(Decision.Search("foo"))
        whenever(queryUrlPredictor.classify("https://search.app/ddbWi")).thenReturn(Decision.Navigate("https://search.app/ddbWi"))
        val input =
            """
            Source: "Guide" https://search.app/ddbWi
            """.trimIndent()
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertEquals("https://search.app/ddbWi", result)
    }

    @Test
    fun `when query contains single url and new line then url is extracted`() {
        val input =
            """
            Source:
            Tom Guide https://search.app/ddbWi
            """.trimIndent()
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertEquals("https://search.app/ddbWi", result)
    }

    @Test
    fun `when url predictor enabled and query contains single url and new line then url is extracted`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify(any())).thenReturn(Decision.Search("foo"))
        whenever(queryUrlPredictor.classify("https://search.app/ddbWi")).thenReturn(Decision.Navigate("https://search.app/ddbWi"))
        val input =
            """
            Source:
            Tom Guide https://search.app/ddbWi
            """.trimIndent()
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertEquals("https://search.app/ddbWi", result)
    }

    @Test
    fun `when query contains single url with no scheme then url is extracted and scheme added`() {
        val input = "pre text duckduckgo.com post text"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertEquals("http://duckduckgo.com", result)
    }

    @Test
    fun `when url predictor enabled and query contains single url with no scheme then url is extracted and scheme added`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify(any())).thenReturn(Decision.Search("foo"))
        whenever(queryUrlPredictor.classify("duckduckgo.com")).thenReturn(Decision.Navigate("http://duckduckgo.com"))
        val input = "pre text duckduckgo.com post text"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertEquals("http://duckduckgo.com", result)
    }

    @Test
    fun `when query contains multiple urls then search query built`() {
        val input = "https://duckduckgo.com https://google.com"
        val expected = "https%3A%2F%2Fduckduckgo.com%20https%3A%2F%2Fgoogle.com"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertDuckDuckGoSearchQuery(expected, result)
    }

    @Test
    fun `when url predictor enabled and query contains multiple urls then search query built`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify(any())).thenReturn(Decision.Search("foo"))
        whenever(queryUrlPredictor.classify("https://duckduckgo.com")).thenReturn(Decision.Navigate("https://duckduckgo.com"))
        whenever(queryUrlPredictor.classify("https://google.com")).thenReturn(Decision.Navigate("https://google.com"))
        val input = "https://duckduckgo.com https://google.com"
        val expected = "https%3A%2F%2Fduckduckgo.com%20https%3A%2F%2Fgoogle.com"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertDuckDuckGoSearchQuery(expected, result)
    }

    @Test
    fun `when query contains url and extract url is false then search query built`() {
        val input = "Source: Towards Data Science  https://search.app/2W427"
        val expected = "Source%3A%20Towards%20Data%20Science%20%20https%3A%2F%2Fsearch.app%2F2W427"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = false)
        assertDuckDuckGoSearchQuery(expected, result)
    }

    @Test
    fun `when url predictor enabled and query contains url and extract url is false then search query built`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify(any())).thenReturn(Decision.Search("foo"))
        val input = "Source: Towards Data Science  https://search.app/2W427"
        val expected = "Source%3A%20Towards%20Data%20Science%20%20https%3A%2F%2Fsearch.app%2F2W427"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = false)
        assertDuckDuckGoSearchQuery(expected, result)
    }

    @Test
    fun `when url predictor config disabled then url predictor not used`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        androidBrowserConfigFeature.useUrlPredictor().setRawStoredState(State(false))
        testee.onPrivacyConfigDownloaded()
        val input = "foo"
        val expected = "foo"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = false)
        assertDuckDuckGoSearchQuery(expected, result)
        verify(queryUrlPredictor, never()).classify(any())
    }

    @Test
    fun `when url predictor config enabled then url predictor used`() {
        val testee = createTestee(useUrlPredictorEnabled = false)
        whenever(queryUrlPredictor.classify("foo")).thenReturn(Decision.Search("foo"))
        androidBrowserConfigFeature.useUrlPredictor().setRawStoredState(State(true))
        testee.onPrivacyConfigDownloaded()
        val input = "foo"
        val expected = "foo"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = false)
        assertDuckDuckGoSearchQuery(expected, result)
        verify(queryUrlPredictor).classify("foo")
    }

    @Test
    fun `when url predictor is not ready and config enabled then url predictor not used`() {
        val testee = createTestee(useUrlPredictorEnabled = false)
        whenever(queryUrlPredictor.classify("foo")).thenReturn(Decision.Search("foo"))
        whenever(queryUrlPredictor.isReady()).thenReturn(false)
        androidBrowserConfigFeature.useUrlPredictor().setRawStoredState(State(true))
        testee.onPrivacyConfigDownloaded()
        val input = "foo"
        val expected = "foo"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = false)
        assertDuckDuckGoSearchQuery(expected, result)
        verify(queryUrlPredictor, never()).classify("foo")
    }

    @Test
    fun `when url predictor enabled and extract url enabled but not url then search returned`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        whenever(queryUrlPredictor.classify("no url here")).thenReturn(Decision.Search("no url here"))
        val input = "no url here"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        assertDuckDuckGoSearchQuery("no%20url%20here", result)
        verify(queryUrlPredictor).classify("no url here")
    }

    @Test
    fun `when url predictor enabled and extract url enabled and cycle detected then loop breaks`() {
        val testee = createTestee(useUrlPredictorEnabled = true)
        // This is a theoretical edge case: predictor keeps returning Search for URLs
        whenever(queryUrlPredictor.classify(any())).thenReturn(Decision.Search("foo"))
        val input = "Visit example.com or test.com for info"
        val result = testee.convertQueryToUrl(input, queryOrigin = QueryOrigin.FromUser, extractUrlFromQuery = true)
        // Should eventually break out and create a search query
        assertDuckDuckGoSearchQuery("Visit%20example.com%20or%20test.com%20for%20info", result)
    }

    private fun createTestee(useUrlPredictorEnabled: Boolean): QueryUrlConverter {
        androidBrowserConfigFeature.useUrlPredictor().setRawStoredState(State(useUrlPredictorEnabled))
        val converter = QueryUrlConverter(
            requestRewriter,
            androidBrowserConfigFeature,
            coroutineTestRule.testScope,
            coroutineTestRule.testDispatcherProvider,
            queryUrlPredictor,
        )
        return converter
    }

    private fun assertDuckDuckGoSearchQuery(
        query: String,
        url: String,
    ) {
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
