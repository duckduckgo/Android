/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.statistics.user_segments

import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.StatisticsPixelName
import com.duckduckgo.app.statistics.user_segments.SegmentCalculation.ActivityType.DUCKAI
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UserSegmentsPixelSenderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val usageHistory: UsageHistory = mock()
    private val segmentCalculation: SegmentCalculation = mock()
    private val pixel: Pixel = mock()
    private val crashLogger: CrashLogger = mock()

    private lateinit var testee: UserSegmentsPixelSender

    @Before
    fun setup() {
        testee = UserSegmentsPixelSender(
            usageHistory = usageHistory,
            segmentCalculation = segmentCalculation,
            pixel = pixel,
            coroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            crashLogger = crashLogger,
        )
    }

    @Test
    fun whenDuckAiRetentionAtbRefreshedAndAtbChangesThenPixelFired() = runTest {
        val usageHistoryList = listOf("v123-1", "v123-2")
        val userSegment = SegmentCalculation.UserSegment(
            activityType = "duckai",
            cohortAtb = "v123-1",
            newSetAtb = "v123-2",
            countAsWau = true,
            countAsMau = "tttt",
            segmentsToday = listOf("first_month"),
            segmentsPrevWeek = emptyList(),
        )
        whenever(usageHistory.getDuckAiHistory()).thenReturn(usageHistoryList)
        whenever(segmentCalculation.computeUserSegmentForActivityType(DUCKAI, usageHistoryList)).thenReturn(userSegment)

        testee.onDuckAiRetentionAtbRefreshed("v123-1", "v123-2")
        advanceUntilIdle()

        verify(usageHistory).addDuckAiUsage("v123-2")
        verify(usageHistory).getDuckAiHistory()
        verify(segmentCalculation).computeUserSegmentForActivityType(DUCKAI, usageHistoryList)
        verify(pixel).fire(
            pixelName = eq(StatisticsPixelName.RETENTION_SEGMENTS.pixelName),
            parameters = eq(userSegment.toPixelParams()),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun whenDuckAiRetentionAtbRefreshedAndAtbUnchangedThenPixelNotFired() = runTest {
        whenever(usageHistory.getDuckAiHistory()).thenReturn(listOf("v123-1"))

        testee.onDuckAiRetentionAtbRefreshed("v123-1", "v123-1")
        advanceUntilIdle()

        verify(segmentCalculation, never()).computeUserSegmentForActivityType(any(), any())
        verify(pixel, never()).fire(
            pixelName = eq(StatisticsPixelName.RETENTION_SEGMENTS.pixelName),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun whenDuckAiRetentionAtbRefreshedThenAppAndSearchHistoriesNotUsed() = runTest {
        val usageHistoryList = listOf("v123-1", "v123-2")
        val userSegment = SegmentCalculation.UserSegment(
            activityType = "duckai",
            cohortAtb = "v123-1",
            newSetAtb = "v123-2",
            countAsWau = true,
            countAsMau = "tttt",
            segmentsToday = listOf("first_month"),
            segmentsPrevWeek = emptyList(),
        )
        whenever(usageHistory.getDuckAiHistory()).thenReturn(usageHistoryList)
        whenever(segmentCalculation.computeUserSegmentForActivityType(DUCKAI, usageHistoryList)).thenReturn(userSegment)

        testee.onDuckAiRetentionAtbRefreshed("v123-1", "v123-2")
        advanceUntilIdle()

        verify(usageHistory, never()).addAppUsage(any())
        verify(usageHistory, never()).addSearchUsage(any())
    }

    @Test
    fun whenAppRetentionAtbRefreshedAndAtbUnchangedThenPixelNotFired() = runTest {
        whenever(usageHistory.getAppUsageHistory()).thenReturn(listOf("v123-1"))

        testee.onAppRetentionAtbRefreshed("v123-1", "v123-1")
        advanceUntilIdle()

        verify(segmentCalculation, never()).computeUserSegmentForActivityType(any(), any())
        verify(pixel, never()).fire(
            pixelName = eq(StatisticsPixelName.RETENTION_SEGMENTS.pixelName),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun whenSearchRetentionAtbRefreshedAndAtbUnchangedThenPixelNotFired() = runTest {
        whenever(usageHistory.getSearchUsageHistory()).thenReturn(listOf("v123-1"))

        testee.onSearchRetentionAtbRefreshed("v123-1", "v123-1")
        advanceUntilIdle()

        verify(segmentCalculation, never()).computeUserSegmentForActivityType(any(), any())
        verify(pixel, never()).fire(
            pixelName = eq(StatisticsPixelName.RETENTION_SEGMENTS.pixelName),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
    }
}
