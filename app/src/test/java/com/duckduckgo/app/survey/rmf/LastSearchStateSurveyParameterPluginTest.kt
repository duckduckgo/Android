package com.duckduckgo.app.survey.rmf

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.history.api.HistoryEntry
import com.duckduckgo.history.api.NavigationHistory
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class LastSearchStateSurveyParameterPluginTest {

    @Mock private lateinit var navigationHistory: NavigationHistory

    @Mock private lateinit var currentTimeProvider: CurrentTimeProvider

    private lateinit var plugin: LastSearchStateSurveyParameterPlugin

    private val now = LocalDateTime.of(2024, 6, 18, 12, 0)

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        plugin = LastSearchStateSurveyParameterPlugin(navigationHistory, currentTimeProvider)
        whenever(currentTimeProvider.localDateTimeNow()).thenReturn(now)
    }

    @Test
    fun whenNoHistoryThenReturnsNone() = runTest {
        whenever(navigationHistory.getHistory()).thenReturn(flowOf(emptyList()))

        assertEquals("none", plugin.evaluate("last_search_state"))
    }

    @Test
    fun whenNoSerpEntriesThenReturnsNone() = runTest {
        val visitedPage = HistoryEntry.VisitedPage(
            url = Uri.parse("https://example.com"),
            title = "Example",
            visits = listOf(now.minusDays(1)),
        )
        whenever(navigationHistory.getHistory()).thenReturn(flowOf(listOf(visitedPage)))

        assertEquals("none", plugin.evaluate("last_search_state"))
    }

    @Test
    fun whenLastSearchWasLessThan2DaysAgoThenReturnsDay() = runTest {
        val serp = serpEntry(lastVisit = now.minusHours(6))
        whenever(navigationHistory.getHistory()).thenReturn(flowOf(listOf(serp)))

        assertEquals("day", plugin.evaluate("last_search_state"))
    }

    @Test
    fun whenLastSearchWas1DayAgoThenReturnsDay() = runTest {
        val serp = serpEntry(lastVisit = now.minusDays(1))
        whenever(navigationHistory.getHistory()).thenReturn(flowOf(listOf(serp)))

        assertEquals("day", plugin.evaluate("last_search_state"))
    }

    @Test
    fun whenLastSearchWas2DaysAgoThenReturnsWeek() = runTest {
        val serp = serpEntry(lastVisit = now.minusDays(2))
        whenever(navigationHistory.getHistory()).thenReturn(flowOf(listOf(serp)))

        assertEquals("week", plugin.evaluate("last_search_state"))
    }

    @Test
    fun whenLastSearchWas7DaysAgoThenReturnsWeek() = runTest {
        val serp = serpEntry(lastVisit = now.minusDays(7))
        whenever(navigationHistory.getHistory()).thenReturn(flowOf(listOf(serp)))

        assertEquals("week", plugin.evaluate("last_search_state"))
    }

    @Test
    fun whenLastSearchWas8DaysAgoThenReturnsNone() = runTest {
        val serp = serpEntry(lastVisit = now.minusDays(8))
        whenever(navigationHistory.getHistory()).thenReturn(flowOf(listOf(serp)))

        assertEquals("none", plugin.evaluate("last_search_state"))
    }

    @Test
    fun whenMultipleSerpEntriesUseMostRecentVisit() = runTest {
        val recentSerp = serpEntry(lastVisit = now.minusDays(3))
        val oldSerp = serpEntry(lastVisit = now.minusDays(10), query = "old query")
        whenever(navigationHistory.getHistory()).thenReturn(flowOf(listOf(oldSerp, recentSerp)))

        assertEquals("week", plugin.evaluate("last_search_state"))
    }

    @Test
    fun whenMatchesReturnsTrueForCorrectKey() {
        assertEquals(true, plugin.matches("last_search_state"))
    }

    @Test
    fun whenMatchesReturnsFalseForOtherKeys() {
        assertEquals(false, plugin.matches("some_other_key"))
    }

    private fun serpEntry(
        lastVisit: LocalDateTime,
        query: String = "duckduckgo",
    ) = HistoryEntry.VisitedSERP(
        url = Uri.parse("https://duckduckgo.com/?q=$query"),
        title = "Search results",
        query = query,
        visits = listOf(lastVisit),
    )
}
