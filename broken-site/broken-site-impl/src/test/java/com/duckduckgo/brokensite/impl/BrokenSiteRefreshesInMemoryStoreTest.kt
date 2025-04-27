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

package com.duckduckgo.brokensite.impl

import android.net.Uri
import com.duckduckgo.brokensite.api.RefreshPattern
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class BrokenSiteRefreshesInMemoryStoreTest {

    private lateinit var store: RealBrokenSiteRefreshesInMemoryStore
    private lateinit var testUrl: Uri
    private lateinit var baseTime: LocalDateTime

    @Before
    fun setup() {
        store = RealBrokenSiteRefreshesInMemoryStore()
        baseTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0)
        testUrl = mock(Uri::class.java)
        whenever(testUrl.toString()).thenReturn("https://example.com")
    }

    @Test
    fun whenStoreInitializedThenNoRefreshPatternsFound() = runTest {
        val patterns = store.getRefreshPatterns(baseTime)
        assertTrue(patterns.isEmpty())
    }

    @Test
    fun whenResetCalledThenRefreshCountReset() = runTest {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(5))

        store.resetRefreshCount()

        val patterns = store.getRefreshPatterns(baseTime.plusSeconds(10))
        assertTrue(patterns.isEmpty())
    }

    @Test
    fun whenRefreshAddedForNewUrlThenPreviousDataReplaced() = runTest {
        store.addRefresh(testUrl, baseTime)

        val newUrl = mock(Uri::class.java)
        whenever(newUrl.toString()).thenReturn("https://newUrl.com")

        store.addRefresh(newUrl, baseTime.plusSeconds(5))

        val patterns = store.getRefreshPatterns(baseTime.plusSeconds(10))
        assertTrue(patterns.isEmpty())

        store.addRefresh(newUrl, baseTime.plusSeconds(10))

        val updatedPatterns = store.getRefreshPatterns(baseTime.plusSeconds(15))
        assertEquals(1, updatedPatterns.size)
        assertTrue(updatedPatterns.any { it.pattern == RefreshPattern.TWICE_IN_12_SECONDS })
    }

    @Test
    fun whenRefreshAddedForSameUrlThenRefreshAddedToExistingList() = runTest {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(5))

        val patterns = store.getRefreshPatterns(baseTime.plusSeconds(10))
        assertEquals(1, patterns.size)
        assertTrue(patterns.any { it.pattern == RefreshPattern.TWICE_IN_12_SECONDS })
    }

    @Test
    fun whenTwoRefreshesOccurWithin12SecondsThenPatternDetected() = runTest {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(5))

        val patterns = store.getRefreshPatterns(baseTime.plusSeconds(10))
        assertEquals(1, patterns.size)
        val detectedPattern = patterns.first()
        assertEquals(RefreshPattern.TWICE_IN_12_SECONDS, detectedPattern.pattern)
        assertEquals(1, detectedPattern.count)
    }

    @Test
    fun whenTwoRefreshesOccurAfter12SecondsThenNoTwicePatternDetected() = runTest {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(15))

        val patterns = store.getRefreshPatterns(baseTime.plusSeconds(20))
        assertTrue(patterns.isEmpty() || patterns.none { it.pattern == RefreshPattern.TWICE_IN_12_SECONDS })
    }

    @Test
    fun whenThreeRefreshesOccurWithin20SecondsThenTwiceAndThricePatternDetected() = runTest {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(5))
        store.addRefresh(testUrl, baseTime.plusSeconds(10))

        val patterns = store.getRefreshPatterns(baseTime.plusSeconds(15))
        assertEquals(2, patterns.size)

        val twicePattern = patterns.first { it.pattern == RefreshPattern.TWICE_IN_12_SECONDS }
        assertEquals(1, twicePattern.count)

        val thricePattern = patterns.first { it.pattern == RefreshPattern.THRICE_IN_20_SECONDS }
        assertEquals(1, thricePattern.count)
    }

    @Test
    fun whenThreeRefreshesOccurAfter20SecondsThenNoThricePatternDetected() = runTest {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(5))
        store.addRefresh(testUrl, baseTime.plusSeconds(20))

        val patterns = store.getRefreshPatterns(baseTime.plusSeconds(25))
        assertTrue(patterns.isEmpty())
    }

    @Test
    fun whenMultiplePatternsOccurThenAllAreDetectedWithCorrectCounts() = runTest {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))
        store.addRefresh(testUrl, baseTime.plusSeconds(10))
        store.addRefresh(testUrl, baseTime.plusSeconds(15))
        store.addRefresh(testUrl, baseTime.plusSeconds(20))

        val patterns = store.getRefreshPatterns(baseTime.plusSeconds(25))
        assertEquals(2, patterns.size)

        val twicePattern = patterns.first { it.pattern == RefreshPattern.TWICE_IN_12_SECONDS }
        assertEquals(2, twicePattern.count)

        val thricePattern = patterns.first { it.pattern == RefreshPattern.THRICE_IN_20_SECONDS }
        assertEquals(1, thricePattern.count)
    }

    @Test
    fun whenRefreshesAreOlderThanWindowThenTheyArePruned() = runTest {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(5))

        val patternsBeforePruning = store.getRefreshPatterns(baseTime.plusSeconds(10))
        assertEquals(1, patternsBeforePruning.size)

        val patterns = store.getRefreshPatterns(baseTime.plusSeconds(25))
        assertTrue(patterns.isEmpty())
    }
}
