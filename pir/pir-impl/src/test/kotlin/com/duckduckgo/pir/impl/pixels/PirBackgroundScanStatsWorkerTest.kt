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

package com.duckduckgo.pir.impl.pixels

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.common.PirJobConstants
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class PirBackgroundScanStatsWorkerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockPirPixelSender: PirPixelSender = mock()
    private val mockPirRepository: PirRepository = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mock()
    }

    @Test
    fun whenLastRunIsZeroThenScanFrequencyWithinThresholdIsFalse() = runTest {
        val currentTimeMs = System.currentTimeMillis()
        whenever(mockPirRepository.latestBackgroundScanRunInMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(currentTimeMs)

        val worker = TestListenableWorkerBuilder<PirBackgroundScanStatsWorker>(context = context).build()
        worker.pirPixelSender = mockPirPixelSender
        worker.pirRepository = mockPirRepository
        worker.currentTimeProvider = mockCurrentTimeProvider

        val result = worker.doWork()

        verify(mockPirPixelSender).reportBackgroundScanStats(scanFrequencyWithinThreshold = false)
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun whenDiffIsWithin48HoursThenScanFrequencyWithinThresholdIsTrue() = runTest {
        val currentTimeMs = 100_000_000L
        val lastRunMs = currentTimeMs - TimeUnit.HOURS.toMillis(24) // 24 hours ago
        whenever(mockPirRepository.latestBackgroundScanRunInMs()).thenReturn(lastRunMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(currentTimeMs)

        val worker = TestListenableWorkerBuilder<PirBackgroundScanStatsWorker>(context = context).build()
        worker.pirPixelSender = mockPirPixelSender
        worker.pirRepository = mockPirRepository
        worker.currentTimeProvider = mockCurrentTimeProvider

        val result = worker.doWork()

        verify(mockPirPixelSender).reportBackgroundScanStats(scanFrequencyWithinThreshold = true)
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun whenDiffIsExactly48HoursThenScanFrequencyWithinThresholdIsTrue() = runTest {
        val currentTimeMs = 100_000_000L
        val lastRunMs = currentTimeMs - TimeUnit.HOURS.toMillis(PirJobConstants.BG_SCAN_RUN_THRESHOLD_HRS) // Exactly 48 hours ago
        whenever(mockPirRepository.latestBackgroundScanRunInMs()).thenReturn(lastRunMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(currentTimeMs)

        val worker = TestListenableWorkerBuilder<PirBackgroundScanStatsWorker>(context = context).build()
        worker.pirPixelSender = mockPirPixelSender
        worker.pirRepository = mockPirRepository
        worker.currentTimeProvider = mockCurrentTimeProvider

        val result = worker.doWork()

        verify(mockPirPixelSender).reportBackgroundScanStats(scanFrequencyWithinThreshold = true)
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun whenDiffIsMoreThan48HoursThenScanFrequencyWithinThresholdIsFalse() = runTest {
        val currentTimeMs = 100_000_000L
        val lastRunMs = currentTimeMs - TimeUnit.HOURS.toMillis(72) // 72 hours ago
        whenever(mockPirRepository.latestBackgroundScanRunInMs()).thenReturn(lastRunMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(currentTimeMs)

        val worker = TestListenableWorkerBuilder<PirBackgroundScanStatsWorker>(context = context).build()
        worker.pirPixelSender = mockPirPixelSender
        worker.pirRepository = mockPirRepository
        worker.currentTimeProvider = mockCurrentTimeProvider

        val result = worker.doWork()

        verify(mockPirPixelSender).reportBackgroundScanStats(scanFrequencyWithinThreshold = false)
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun whenDoWorkThenAlwaysReturnsSuccess() = runTest {
        whenever(mockPirRepository.latestBackgroundScanRunInMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(System.currentTimeMillis())

        val worker = TestListenableWorkerBuilder<PirBackgroundScanStatsWorker>(context = context).build()
        worker.pirPixelSender = mockPirPixelSender
        worker.pirRepository = mockPirRepository
        worker.currentTimeProvider = mockCurrentTimeProvider

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }
}
