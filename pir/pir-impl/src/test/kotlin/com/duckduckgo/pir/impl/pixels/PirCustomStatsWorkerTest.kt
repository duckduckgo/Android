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

package com.duckduckgo.pir.impl.pixels

import android.content.Context
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.store.PirRepository
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
import java.util.concurrent.TimeUnit

class PirCustomStatsWorkerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockPirRepository: PirRepository = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockOptOutSubmitRateCalculator: OptOutSubmitRateCalculator = mock()
    private val mockPirPixelSender: PirPixelSender = mock()
    private val context: Context = mock()

    private lateinit var worker: PirCustomStatsWorker

    // Test data
    // January 15, 2024 10:00:00 UTC
    private val baseTime = 1705309200000L
    private val oneHour = TimeUnit.HOURS.toMillis(1)
    private val twentyFourHours = TimeUnit.HOURS.toMillis(24)

    private val testBroker1 = Broker(
        name = "test-broker-1",
        fileName = "test-broker-1.json",
        url = "https://test-broker-1.com",
        version = "1.0",
        parent = null,
        addedDatetime = baseTime,
        removedAt = 0L,
    )

    private val testBroker2 = Broker(
        name = "test-broker-2",
        fileName = "test-broker-2.json",
        url = "https://test-broker-2.com",
        version = "1.0",
        parent = null,
        addedDatetime = baseTime,
        removedAt = 0L,
    )

    private val testProfileQuery = ProfileQuery(
        id = 1L,
        firstName = "John",
        lastName = "Doe",
        city = "New York",
        state = "NY",
        addresses = emptyList(),
        birthYear = 1990,
        fullName = "John Doe",
        age = 33,
        deprecated = false,
    )

    @Before
    fun setUp() {
        worker = TestListenableWorkerBuilder
            .from(context, PirCustomStatsWorker::class.java)
            .build()
        worker.pirRepository = mockPirRepository
        worker.currentTimeProvider = mockCurrentTimeProvider
        worker.optOutSubmitRateCalculator = mockOptOutSubmitRateCalculator
        worker.pirPixelSender = mockPirPixelSender
    }

    @Test
    fun whenFirstRunThenShouldFirePixel() = runTest {
        val now = baseTime
        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                any(),
                any(),
                any(),
            ),
        ).thenReturn(0.5)

        worker.doWork()

        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 0.5,
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now - twentyFourHours)
    }

    @Test
    fun whenLessThan24HoursPassedThenShouldNotFirePixel() = runTest {
        val startDate = baseTime
        val now = baseTime + oneHour // Only 1 hour passed

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(startDate)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        worker.doWork()
        verify(mockPirRepository, never()).getAllActiveBrokerObjects()
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(any(), any())
        verify(mockPirRepository, never()).setCustomStatsPixelsLastSentMs(any())
    }

    @Test
    fun whenMoreThan24HoursPassedThenShouldFirePixel() = runTest {
        val startDate = baseTime
        val now = baseTime + twentyFourHours + oneHour // 25 hours passed

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(startDate)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                any(),
                any(),
                any(),
            ),
        ).thenReturn(0.75)

        worker.doWork()
        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 0.75,
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now - twentyFourHours)
    }

    @Test
    fun whenExactly24HoursPassedThenShouldNotFirePixel() = runTest {
        val startDate = baseTime
        val now = baseTime + twentyFourHours // Exactly 24 hours

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(startDate)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        worker.doWork()
        verify(mockPirRepository, never()).getAllActiveBrokerObjects()
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(any(), any())
    }

    @Test
    fun whenNoActiveBrokersThenShouldNotFirePixel() = runTest {
        val now = baseTime
        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))

        worker.doWork()
        verify(mockOptOutSubmitRateCalculator, never()).calculateOptOutSubmitRate(
            any(),
            any(),
            any(),
        )
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(any(), any())
        verify(mockPirRepository, never()).setCustomStatsPixelsLastSentMs(any())
    }

    @Test
    fun whenNoUserProfilesThenShouldNotFirePixel() = runTest {
        val now = baseTime
        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(emptyList())

        worker.doWork()
        verify(mockOptOutSubmitRateCalculator, never()).calculateOptOutSubmitRate(
            any(),
            any(),
            any(),
        )
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(any(), any())
        verify(mockPirRepository, never()).setCustomStatsPixelsLastSentMs(any())
    }

    @Test
    fun whenMultipleBrokersThenShouldFirePixelForEach() = runTest {
        val now = baseTime
        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(
            listOf(
                testBroker1,
                testBroker2,
            ),
        )
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                testBroker1.name,
                0L,
                now - twentyFourHours,
            ),
        )
            .thenReturn(0.5)
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                testBroker2.name,
                0L,
                now - twentyFourHours,
            ),
        )
            .thenReturn(0.8)

        worker.doWork()
        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 0.5,
        )
        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker2.url,
            optOutSuccessRate = 0.8,
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now - twentyFourHours)
    }

    @Test
    fun whenSuccessRateIsNullThenShouldNotFirePixelForThatBroker() = runTest {
        val now = baseTime
        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(
            listOf(
                testBroker1,
                testBroker2,
            ),
        )
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                testBroker1.name,
                0L,
                now - twentyFourHours,
            ),
        )
            .thenReturn(0.5)
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                testBroker2.name,
                0L,
                now - twentyFourHours,
            ),
        )
            .thenReturn(null)

        worker.doWork()
        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 0.5,
        )
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = eq(testBroker2.url),
            optOutSuccessRate = any(),
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now - twentyFourHours)
    }

    @Test
    fun whenAllSuccessRatesAreNullThenShouldNotFireAnyPixels() = runTest {
        val now = baseTime
        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                any(),
                any(),
                any(),
            ),
        ).thenReturn(null)

        worker.doWork()
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(any(), any())
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now - twentyFourHours)
    }

    @Test
    fun whenShouldFirePixelThenUsesCorrectDateRange() = runTest {
        val startDate = baseTime
        val now = baseTime + twentyFourHours + oneHour
        val expectedEndDate = now - twentyFourHours

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(startDate)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                any(),
                any(),
                any(),
            ),
        ).thenReturn(0.5)

        worker.doWork()

        verify(mockOptOutSubmitRateCalculator).calculateOptOutSubmitRate(
            brokerName = testBroker1.name,
            startDateMs = startDate,
            endDateMs = expectedEndDate,
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(expectedEndDate)
    }

    @Test
    fun whenMultipleBrokersWithMixedSuccessRatesThenFiresPixelsForNonNullRates() = runTest {
        val now = baseTime
        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(
            listOf(
                testBroker1,
                testBroker2,
            ),
        )
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                testBroker1.name,
                0L,
                now - twentyFourHours,
            ),
        )
            .thenReturn(null)
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                testBroker2.name,
                0L,
                now - twentyFourHours,
            ),
        )
            .thenReturn(0.9)

        worker.doWork()
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = eq(testBroker1.url),
            optOutSuccessRate = any(),
        )
        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker2.url,
            optOutSuccessRate = 0.9,
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now - twentyFourHours)
    }

    @Test
    fun whenShouldFirePixelButNoBrokersAndNoProfilesThenReturnsSuccess() = runTest {
        val now = baseTime
        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(emptyList())

        worker.doWork()

        verify(mockOptOutSubmitRateCalculator, never()).calculateOptOutSubmitRate(
            any(),
            any(),
            any(),
        )
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(any(), any())
        verify(mockPirRepository, never()).setCustomStatsPixelsLastSentMs(any())
    }

    @Test
    fun whenShouldFirePixelButNoBrokersWithProfilesThenReturnsSuccess() = runTest {
        val now = baseTime
        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))

        worker.doWork()

        verify(mockOptOutSubmitRateCalculator, never()).calculateOptOutSubmitRate(
            any(),
            any(),
            any(),
        )
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(any(), any())
        verify(mockPirRepository, never()).setCustomStatsPixelsLastSentMs(any())
    }

    @Test
    fun whenShouldFirePixelButNoProfilesWithBrokersThenReturnsSuccess() = runTest {
        val now = baseTime
        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(emptyList())

        worker.doWork()

        verify(mockOptOutSubmitRateCalculator, never()).calculateOptOutSubmitRate(
            any(),
            any(),
            any(),
        )
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(any(), any())
        verify(mockPirRepository, never()).setCustomStatsPixelsLastSentMs(any())
    }
}
