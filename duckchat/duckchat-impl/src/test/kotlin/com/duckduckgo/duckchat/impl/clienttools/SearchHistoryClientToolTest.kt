/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.clienttools

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.history.api.HistoryEntry
import com.duckduckgo.history.api.HistoryEntry.VisitedPage
import com.duckduckgo.history.api.HistoryEntry.VisitedSERP
import com.duckduckgo.history.api.NavigationHistory
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class SearchHistoryClientToolTest {

    private val feature = FakeFeatureToggleFactory.create(AiChatBrowserToolsFeature::class.java)
    private val history: NavigationHistory = mock()
    private val testee = SearchHistoryClientTool(feature, history)
    private val context = ClientToolCallContext(chatTabId = "chat", browserMode = BrowserMode.REGULAR, chatId = "chat-1")
    private val originalZone = TimeZone.getDefault()

    @Before
    fun setUp() = runTest {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))
        whenever(history.isHistoryFeatureAvailable()).thenReturn(true)
        whenever(history.isHistoryUserEnabled()).thenReturn(true)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalZone)
    }

    @Test
    fun whenParentAndToolFlagsEnabledThenToolIsEnabled() {
        feature.self().setRawStoredState(Toggle.State(enable = true))
        assertFalse(testee.isEnabled())

        feature.searchHistory().setRawStoredState(Toggle.State(enable = true))
        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenInputMatchesSchemaThenValid() {
        listOf(
            """{}""",
            """{"query":""}""",
            """{"query":"paris","limit":50}""",
            """{"startDate":"2026-10-01","endDate":"2026-10-01"}""",
            """{"startDate":"2026-10-01T10:00:00Z","endDate":"2026-10-01T12:00:00+02:00"}""",
            JSONObject().put("query", "🦆".repeat(1000)).toString(),
        ).forEach { assertTrue(it, testee.isValidInput(JSONObject(it))) }
    }

    @Test
    fun whenInputBreaksSchemaThenInvalidArguments() = runTest {
        listOf(
            JSONObject().put("query", "a".repeat(1001)).toString(),
            """{"limit":51}""",
            """{"limit":0}""",
            """{"startDate":"yesterday"}""",
            """{"endDate":5}""",
            """{"startDate":"2026-10-02","endDate":"2026-10-01"}""",
            """{"query":"paris","folder":["a"]}""",
        ).forEach { input ->
            assertFalse(input, testee.isValidInput(JSONObject(input)))
            assertEquals(input, ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS), testee.execute(JSONObject(input), context))
        }
    }

    @Test
    fun whenHistoryIsTurnedOffThenUnavailable() = runTest {
        whenever(history.isHistoryUserEnabled()).thenReturn(false)

        assertEquals(ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE), testee.execute(JSONObject(), context))
    }

    @Test
    fun whenNoQueryThenMostRecentVisitsAreReturnedWithUtcTimestamps() = runTest {
        givenHistory(
            page("https://example.com/old", "Old", "2026-10-27T09:30:00"),
            page("https://example.com/new", "New", "2026-10-29T01:15:30"),
        )

        val value = success(testee.execute(JSONObject(), context))

        assertEquals(
            """{"results":[""" +
                """{"title":"New","url":"https:\/\/example.com\/new","visitedAt":"2026-10-29T00:15:30Z"},""" +
                """{"title":"Old","url":"https:\/\/example.com\/old","visitedAt":"2026-10-27T08:30:00Z"}""" +
                """]}""",
            value.toString(),
        )
    }

    @Test
    fun whenAddressesDifferOnlyByQueryThenTheyAppearOnceAtTheLatestVisit() = runTest {
        givenHistory(
            page("https://example.com/a?x=1", "First", "2026-10-27T09:00:00"),
            page("https://example.com/a?x=2#top", "Second", "2026-10-28T09:00:00"),
        )

        val results = success(testee.execute(JSONObject(), context)).getJSONArray("results")

        assertEquals(1, results.length())
        assertEquals("Second", results.getJSONObject(0).getString("title"))
        assertEquals("https://example.com/a", results.getJSONObject(0).getString("url"))
    }

    @Test
    fun whenPageIsNotWebThenItIsLeftOut() = runTest {
        givenHistory(page("file:///sdcard/a.html", "Local", "2026-10-27T09:00:00"))

        assertEquals(0, success(testee.execute(JSONObject(), context)).getJSONArray("results").length())
    }

    @Test
    fun whenQueryIsOnlyInTheQueryStringThenPageDoesNotMatch() = runTest {
        givenHistory(page("https://example.com/a?city=paris", "Something", "2026-10-27T09:00:00"))

        assertEquals(0, urls("""{"query":"paris"}""").size)
    }

    @Test
    fun whenPageMatchesHalfTheWordsThenItIsReturnedAfterPagesMatchingMore() = runTest {
        givenHistory(
            page("https://travel.example/rome", "Rome hotels", "2026-10-29T09:00:00"),
            page("https://travel.example/paris", "Paris hotels", "2026-10-27T09:00:00"),
            page("https://other.example/", "Nothing", "2026-10-29T09:00:00"),
        )

        assertEquals(listOf("https://travel.example/paris", "https://travel.example/rome"), urls("""{"query":"PARIS Hotels"}"""))
    }

    @Test
    fun whenWordsMatchEquallyThenWordStartsRankAboveInnerMatches() = runTest {
        givenHistory(
            page("https://example.com/a", "Superman", "2026-10-29T09:00:00"),
            page("https://example.com/b", "Man of steel", "2026-10-27T09:00:00"),
        )

        assertEquals(listOf("https://example.com/b", "https://example.com/a"), urls("""{"query":"man"}"""))
    }

    @Test
    fun whenMatchIsEqualThenPagesVisitedOnMoreDaysRankFirst() = runTest {
        givenHistory(
            page("https://example.com/once", "Paris", "2026-10-29T09:00:00"),
            page("https://example.com/often", "Paris", "2026-10-20T09:00:00", "2026-10-21T09:00:00"),
        )

        assertEquals(listOf("https://example.com/often", "https://example.com/once"), urls("""{"query":"paris"}"""))
    }

    @Test
    fun whenHostOrPathMatchesThenPageMatches() = runTest {
        givenHistory(
            page("https://paris.example/", "Home", "2026-10-29T09:00:00"),
            page("https://example.com/guides/paris", "Guide", "2026-10-28T09:00:00"),
        )

        assertEquals(2, urls("""{"query":"paris"}""").size)
    }

    @Test
    fun whenDateOnlyBoundsThenTheyCoverTheWholeUtcDay() = runTest {
        givenHistory(
            // 2026-10-27T23:30:00Z in Madrid time.
            page("https://example.com/late", "Late", "2026-10-28T00:30:00"),
            // 2026-10-28T00:30:00Z.
            page("https://example.com/early", "Early", "2026-10-28T01:30:00"),
            page("https://example.com/next", "Next", "2026-10-29T09:00:00"),
        )

        assertEquals(listOf("https://example.com/early"), urls("""{"startDate":"2026-10-28","endDate":"2026-10-28"}"""))
    }

    @Test
    fun whenOnlyOlderVisitsAreInRangeThenVisitedAtIsTheLatestInRange() = runTest {
        givenHistory(page("https://example.com/a", "A", "2026-10-20T10:00:00", "2026-10-29T10:00:00"))

        val result = success(testee.execute(JSONObject("""{"endDate":"2026-10-25"}"""), context)).getJSONArray("results").getJSONObject(0)

        assertEquals("2026-10-20T08:00:00Z", result.getString("visitedAt"))
    }

    @Test
    fun whenLimitGivenThenResultsStopThere() = runTest {
        givenHistory(
            page("https://example.com/1", "1", "2026-10-21T10:00:00"),
            page("https://example.com/2", "2", "2026-10-22T10:00:00"),
            page("https://example.com/3", "3", "2026-10-23T10:00:00"),
        )

        assertEquals(listOf("https://example.com/3", "https://example.com/2"), urls("""{"limit":2}"""))
    }

    @Test
    fun whenSerpIsVisitedThenItsAddressHasNoSearchTerms() = runTest {
        givenHistory(
            VisitedSERP(
                Uri.parse("https://duckduckgo.com/?q=paris"),
                "paris at DuckDuckGo",
                "paris",
                listOf(LocalDateTime.parse("2026-10-27T09:00:00")),
            ),
        )

        assertEquals(listOf("https://duckduckgo.com/"), urls("{}"))
    }

    private suspend fun urls(input: String): List<String> {
        val results = success(testee.execute(JSONObject(input), context)).getJSONArray("results")
        return (0 until results.length()).map { results.getJSONObject(it).getString("url") }
    }

    private fun givenHistory(vararg entries: HistoryEntry) {
        whenever(history.getHistory()).thenReturn(flowOf(entries.toList()))
    }

    private fun page(
        url: String,
        title: String,
        vararg visits: String,
    ) = VisitedPage(Uri.parse(url), title, visits.map { LocalDateTime.parse(it) })

    private fun success(result: ClientToolResult): JSONObject = (result as ClientToolResult.Success).value
}
