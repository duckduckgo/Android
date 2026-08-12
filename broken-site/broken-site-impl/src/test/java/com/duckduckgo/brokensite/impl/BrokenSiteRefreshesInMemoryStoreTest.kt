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
import com.duckduckgo.brokensite.api.RefreshPattern.THRICE_IN_20_SECONDS
import com.duckduckgo.brokensite.api.RefreshPattern.TWICE_IN_12_SECONDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import java.time.LocalDateTime

@RunWith(JUnit4::class)
class BrokenSiteRefreshesInMemoryStoreTest {

    private lateinit var store: RealBrokenSiteRefreshesInMemoryStore
    private lateinit var testUrl: Uri
    private lateinit var otherUrl: Uri
    private lateinit var baseTime: LocalDateTime
    private lateinit var refreshOwner: RefreshPatternOwner

    @Before
    fun setup() {
        store = RealBrokenSiteRefreshesInMemoryStore()
        baseTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0)
        testUrl = mock(Uri::class.java)
        otherUrl = mock(Uri::class.java)
        refreshOwner = RefreshPatternOwner()
    }

    @Test
    fun whenStoreInitializedThenNoRefreshPatternsFound() {
        assertTrue(store.getRefreshPatterns(baseTime).isEmpty())
    }

    @Test
    fun whenTwoRefreshesOccurWithin12SecondsThenTwicePatternDetected() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(11))

        assertEquals(setOf(TWICE_IN_12_SECONDS), store.getRefreshPatterns(baseTime.plusSeconds(11)))
    }

    @Test
    fun whenTwoRefreshesOccurAt12SecondBoundaryThenNoTwicePatternDetected() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(12))

        assertTrue(store.getRefreshPatterns(baseTime.plusSeconds(12)).isEmpty())
    }

    @Test
    fun whenThreeRefreshesOccurWithin20SecondsThenThricePatternDetected() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(12))
        store.addRefresh(testUrl, baseTime.plusSeconds(19))

        assertEquals(setOf(TWICE_IN_12_SECONDS, THRICE_IN_20_SECONDS), store.getRefreshPatterns(baseTime.plusSeconds(19)))
    }

    @Test
    fun whenThreeRefreshesOccurAt20SecondBoundaryThenNoThricePatternDetected() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(5))
        store.addRefresh(testUrl, baseTime.plusSeconds(20))

        assertEquals(setOf(TWICE_IN_12_SECONDS), store.getRefreshPatterns(baseTime.plusSeconds(20)))
    }

    @Test
    fun whenThreeRefreshesDetectedThenLoadCompletionTimeDoesNotAffectPatterns() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))
        store.addRefresh(testUrl, baseTime.plusSeconds(12))

        assertEquals(setOf(TWICE_IN_12_SECONDS, THRICE_IN_20_SECONDS), store.getRefreshPatterns(baseTime.plusSeconds(40)))
    }

    @Test
    fun whenPatternsConsumedThenNextReadReturnsEmpty() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))
        store.addRefresh(testUrl, baseTime.plusSeconds(12))

        assertEquals(setOf(TWICE_IN_12_SECONDS, THRICE_IN_20_SECONDS), store.getRefreshPatterns(baseTime.plusSeconds(12)))
        assertTrue(store.getRefreshPatterns(baseTime.plusSeconds(12)).isEmpty())
    }

    @Test
    fun whenDifferentOwnerReadsPatternsThenPatternsRemainForDetectionOwner() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))
        store.addRefresh(testUrl, baseTime.plusSeconds(12))

        assertTrue(store.getRefreshPatterns(RefreshPatternOwner(), baseTime.plusSeconds(12)).isEmpty())
        assertEquals(setOf(TWICE_IN_12_SECONDS, THRICE_IN_20_SECONDS), store.getRefreshPatterns(baseTime.plusSeconds(12)))
    }

    @Test
    fun whenDifferentOwnerAddsRefreshThenPreviousOwnersPatternsReset() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))
        store.addRefresh(testUrl, baseTime.plusSeconds(12))

        val otherOwner = RefreshPatternOwner()
        store.addRefresh(otherOwner, testUrl, baseTime.plusSeconds(13))
        store.addRefresh(otherOwner, testUrl, baseTime.plusSeconds(19))

        assertEquals(setOf(TWICE_IN_12_SECONDS), store.getRefreshPatterns(otherOwner, baseTime.plusSeconds(19)))
        assertTrue(store.getRefreshPatterns(baseTime.plusSeconds(19)).isEmpty())
    }

    @Test
    fun whenSamePatternDetectedAgainBeforeConsumptionThenPatternIsCollapsed() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))
        store.addRefresh(testUrl, baseTime.plusSeconds(12))
        store.addRefresh(testUrl, baseTime.plusSeconds(18))

        assertEquals(setOf(TWICE_IN_12_SECONDS, THRICE_IN_20_SECONDS), store.getRefreshPatterns(baseTime.plusSeconds(18)))
    }

    @Test
    fun whenUrlChangesThenRefreshHistoryAndPendingPatternsReset() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))

        store.addRefresh(otherUrl, baseTime.plusSeconds(7))
        store.addRefresh(otherUrl, baseTime.plusSeconds(18))

        assertEquals(setOf(TWICE_IN_12_SECONDS), store.getRefreshPatterns(baseTime.plusSeconds(18)))
        assertTrue(store.isRefreshPatternDetectionValid(otherUrl, baseTime.plusSeconds(18)))
        assertFalse(store.isRefreshPatternDetectionValid(testUrl, baseTime.plusSeconds(18)))
    }

    @Test
    fun whenNoPatternDetectedThenDetectionIsInvalid() {
        assertFalse(store.isRefreshPatternDetectionValid(testUrl, baseTime))
    }

    @Test
    fun whenDetectionUrlMatchesThenDetectionIsValid() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))

        assertTrue(store.isRefreshPatternDetectionValid(testUrl, baseTime.plusSeconds(6)))
        assertFalse(store.isRefreshPatternDetectionValid(otherUrl, baseTime.plusSeconds(6)))
    }

    @Test
    fun whenPatternsConsumedThenDetectionMetadataRemainsValid() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))
        store.getRefreshPatterns(baseTime.plusSeconds(6))

        assertTrue(store.isRefreshPatternDetectionValid(testUrl, baseTime.plusSeconds(6)))
    }

    @Test
    fun whenDetectionIs60SecondsOldThenDetectionIsValid() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))

        assertTrue(store.isRefreshPatternDetectionValid(testUrl, baseTime.plusSeconds(66)))
    }

    @Test
    fun whenDetectionIsOlderThan60SecondsThenDetectionIsInvalid() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))

        assertFalse(store.isRefreshPatternDetectionValid(testUrl, baseTime.plusSeconds(67)))
    }

    @Test
    fun whenDetectionIsExactly60SecondsOldThenPatternsReported() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))
        store.addRefresh(testUrl, baseTime.plusSeconds(12))

        assertEquals(setOf(TWICE_IN_12_SECONDS, THRICE_IN_20_SECONDS), store.getRefreshPatterns(baseTime.plusSeconds(72)))
    }

    @Test
    fun whenDetectionIsOlderThan60SecondsThenPatternsNotReportedAndMetadataCleared() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))
        store.addRefresh(testUrl, baseTime.plusSeconds(12))

        assertTrue(store.getRefreshPatterns(baseTime.plusSeconds(73)).isEmpty())
        assertFalse(store.isRefreshPatternDetectionValid(testUrl, baseTime.plusSeconds(73)))
    }

    @Test
    fun whenForeignOwnerReadsStaleDetectionThenDetectionCleared() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))
        store.addRefresh(testUrl, baseTime.plusSeconds(12))

        assertTrue(store.getRefreshPatterns(RefreshPatternOwner(), baseTime.plusSeconds(73)).isEmpty())
        assertTrue(store.getRefreshPatterns(baseTime.plusSeconds(73)).isEmpty())
        assertFalse(store.isRefreshPatternDetectionValid(testUrl, baseTime.plusSeconds(73)))
    }

    @Test
    fun whenStaleThricePatternFollowedByFreshTwicePatternThenOnlyTwicePatternReported() {
        store.addRefresh(testUrl, baseTime)
        store.addRefresh(testUrl, baseTime.plusSeconds(6))
        store.addRefresh(testUrl, baseTime.plusSeconds(12))

        store.addRefresh(testUrl, baseTime.plusSeconds(73))
        store.addRefresh(testUrl, baseTime.plusSeconds(79))

        assertEquals(setOf(TWICE_IN_12_SECONDS), store.getRefreshPatterns(baseTime.plusSeconds(79)))
    }

    private fun RealBrokenSiteRefreshesInMemoryStore.addRefresh(
        url: Uri,
        localDateTime: LocalDateTime,
    ) {
        addRefresh(refreshOwner, url, localDateTime)
    }

    private fun RealBrokenSiteRefreshesInMemoryStore.getRefreshPatterns(currentDateTime: LocalDateTime): Set<RefreshPattern> {
        return getRefreshPatterns(refreshOwner, currentDateTime)
    }
}
