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
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.history.impl.remoteconfig.HistoryFeature
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.Month.JANUARY

@RunWith(AndroidJUnit4::class)
class HistoryTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockHistoryRepository: HistoryRepository = mock()
    private val mockDuckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockHistoryFeature: HistoryFeature = mock()
    private val dispatcherProvider = coroutineTestRule.testDispatcherProvider

    val testee = RealNavigationHistory(
        mockHistoryRepository,
        mockDuckDuckGoUrlDetector,
        mockCurrentTimeProvider,
        mockHistoryFeature,
        dispatcherProvider,
    )

    @Before
    fun setup() {
        runTest {
            whenever(mockHistoryFeature.shouldStoreHistory).thenReturn(true)
            whenever(mockHistoryRepository.isHistoryUserEnabled(any())).thenReturn(true)
        }
    }

    fun whenUrlIsSerpThenSaveToHistoryWithQueryAndSerpIsTrue() {
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)
        whenever(mockDuckDuckGoUrlDetector.extractQuery(any())).thenReturn("query")

        runTest {
            testee.saveToHistory("url", "title")

            verify(mockHistoryRepository).saveToHistory(eq("url"), eq("title"), eq("query"), eq(true))
        }
    }

    @Test
    fun whenNotSerpUrlThenSaveToHistoryWithoutQueryAndSerpIsFalse() {
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(false)

        runTest {
            testee.saveToHistory("url", "title")

            verify(mockHistoryRepository).saveToHistory(eq("url"), eq("title"), eq(null), eq(false))
        }
    }

    @Test
    fun whenClearOldEntriesThenDeleteOldEntriesIsCalledWith30Days() {
        whenever(mockCurrentTimeProvider.localDateTimeNow()).thenReturn(LocalDateTime.of(2000, JANUARY, 1, 0, 0))
        runTest {
            testee.clearOldEntries()
            verify(mockHistoryRepository).clearEntriesOlderThan(eq(mockCurrentTimeProvider.localDateTimeNow().minusDays(30)))
        }
    }

    @Test
    fun whenShouldStoreHistoryIsFalseThenDoNotSaveToHistory() {
        whenever(mockHistoryFeature.shouldStoreHistory).thenReturn(false)

        runTest {
            testee.saveToHistory("url", "title")

            verify(mockHistoryRepository, never()).saveToHistory(any(), any(), any(), any())
        }
    }

    @Test
    fun whenShouldStoreHistoryIsDisabledByUserThenDoNotSaveToHistory() {
        runTest {
            whenever(mockHistoryRepository.isHistoryUserEnabled(any())).thenReturn(false)

            testee.saveToHistory("url", "title")

            verify(mockHistoryRepository, never()).saveToHistory(any(), any(), any(), any())
        }
    }

    @Test
    fun whenRemoveHistoryEntryByUrlThenRemoveHistoryEntryByUrlCalled() = runTest {
        val url = "https://example.com"

        testee.removeHistoryEntryByUrl(url)

        verify(mockHistoryRepository).removeHistoryEntryByUrl(eq(url))
    }

    @Test
    fun whenRemoveHistoryEntryByQueryThenRemoveHistoryEntryByQueryCalled() = runTest {
        val query = "query"

        testee.removeHistoryEntryByQuery(query)

        verify(mockHistoryRepository).removeHistoryEntryByQuery(eq(query))
    }
}
