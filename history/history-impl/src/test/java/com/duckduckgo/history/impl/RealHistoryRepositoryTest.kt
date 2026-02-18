/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.history.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.history.impl.store.HistoryDao
import com.duckduckgo.history.impl.store.HistoryDataStore
import com.duckduckgo.history.impl.store.HistoryEntryEntity
import com.duckduckgo.history.impl.store.HistoryEntryWithVisits
import com.duckduckgo.history.impl.store.VisitEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.Month.JANUARY

@RunWith(AndroidJUnit4::class)
class RealHistoryRepositoryTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockHistoryDao: HistoryDao = mock()
    private val mockHistoryDataStore: HistoryDataStore = mock()
    private val dispatcherProvider = coroutineTestRule.testDispatcherProvider

    private val testee = RealHistoryRepository(
        mockHistoryDao,
        dispatcherProvider,
        mockHistoryDataStore,
    )

    @Before
    fun setup() = runTest {
        whenever(mockHistoryDao.getHistoryEntriesWithVisits()).thenReturn(emptyList())
    }

    @Test
    fun whenSaveToHistoryThenDaoUpdateOrInsertVisitCalledWithTabId() = runTest {
        testee.saveToHistory("url", "title", "query", true, "tab1")

        verify(mockHistoryDao).updateOrInsertVisit(eq("url"), eq("title"), eq("query"), eq(true), any(), eq("tab1"))
    }

    @Test
    fun whenSaveToHistoryWithNullTitleThenDaoCalledWithEmptyString() = runTest {
        testee.saveToHistory("url", null, "query", true, "tab1")

        verify(mockHistoryDao).updateOrInsertVisit(eq("url"), eq(""), eq("query"), eq(true), any(), eq("tab1"))
    }

    @Test
    fun whenRemoveHistoryForTabThenDaoDeleteHistoryForTabCalled() = runTest {
        testee.removeHistoryForTab("tab1")

        verify(mockHistoryDao).deleteHistoryForTab(eq("tab1"))
    }

    @Test
    fun whenClearHistoryThenDaoDeleteAllCalled() = runTest {
        testee.clearHistory()

        verify(mockHistoryDao).deleteAll()
    }

    @Test
    fun whenRemoveHistoryEntryByUrlThenDaoDeleteEntriesByUrlCalled() = runTest {
        testee.removeHistoryEntryByUrl("https://example.com")

        verify(mockHistoryDao).deleteEntriesByUrl(eq("https://example.com"))
    }

    @Test
    fun whenRemoveHistoryEntryByQueryThenDaoDeleteEntriesByQueryCalled() = runTest {
        testee.removeHistoryEntryByQuery("query")

        verify(mockHistoryDao).deleteEntriesByQuery(eq("query"))
    }

    @Test
    fun whenClearEntriesOlderThanThenDaoDeleteEntriesOlderThanCalled() = runTest {
        val dateTime = LocalDateTime.of(2000, JANUARY, 1, 0, 0)

        testee.clearEntriesOlderThan(dateTime)

        verify(mockHistoryDao).deleteEntriesOlderThan(eq(dateTime))
    }

    @Test
    fun whenIsHistoryUserEnabledThenDataStoreIsCalled() = runTest {
        whenever(mockHistoryDataStore.isHistoryUserEnabled(true)).thenReturn(true)

        val result = testee.isHistoryUserEnabled(true)

        assertTrue(result)
        verify(mockHistoryDataStore).isHistoryUserEnabled(eq(true))
    }

    @Test
    fun whenSetHistoryUserEnabledThenDataStoreIsCalled() = runTest {
        testee.setHistoryUserEnabled(false)

        verify(mockHistoryDataStore).setHistoryUserEnabled(eq(false))
    }

    @Test
    fun whenHasHistoryAndNoEntriesThenReturnsFalse() = runTest {
        val result = testee.hasHistory()

        assertFalse(result)
    }

    @Test
    fun whenHasHistoryAndEntriesExistThenReturnsTrue() = runTest {
        whenever(mockHistoryDao.getHistoryEntriesWithVisits()).thenReturn(
            listOf(historyEntryWithVisits("https://example.com", "Example")),
        )

        val result = testee.hasHistory()

        assertTrue(result)
    }

    @Test
    fun whenGetHistoryThenReturnsEntriesFromDao() = runTest {
        whenever(mockHistoryDao.getHistoryEntriesWithVisits()).thenReturn(
            listOf(
                historyEntryWithVisits("https://example.com", "Example"),
                historyEntryWithVisits("https://other.com", "Other", id = 2),
            ),
        )

        val result = testee.getHistory().first()

        assertEquals(2, result.size)
    }

    @Test
    fun whenGetHistoryWithBlankUrlThenEntryIsFilteredOut() = runTest {
        whenever(mockHistoryDao.getHistoryEntriesWithVisits()).thenReturn(
            listOf(
                historyEntryWithVisits("https://example.com", "Example"),
                historyEntryWithVisits("", "Blank"),
            ),
        )

        val result = testee.getHistory().first()

        assertEquals(1, result.size)
    }

    private fun historyEntryWithVisits(
        url: String,
        title: String,
        id: Long = 1,
    ) = HistoryEntryWithVisits(
        historyEntry = HistoryEntryEntity(id = id, url = url, title = title, query = null, isSerp = false),
        visits = listOf(VisitEntity(historyEntryId = id, timestamp = "2000-01-01T00:00:00", tabId = "tab1")),
    )
}
