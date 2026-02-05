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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
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

class RealOptOut24HourSubmissionSuccessRateReporterTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockPirRepository: PirRepository = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockOptOutSubmitRateCalculator: OptOutSubmitRateCalculator = mock()
    private val mockPirPixelSender: PirPixelSender = mock()
    private val mockSchedulingRepository: PirSchedulingRepository = mock()

    private lateinit var toTest: RealOptOut24HourSubmissionSuccessRateReporter

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
        toTest = RealOptOut24HourSubmissionSuccessRateReporter(
            optOutSubmitRateCalculator = mockOptOutSubmitRateCalculator,
            pirRepository = mockPirRepository,
            currentTimeProvider = mockCurrentTimeProvider,
            pirPixelSender = mockPirPixelSender,
            pirSchedulingRepository = mockSchedulingRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenFirstRunThenShouldFirePixel() = runTest {
        val now = baseTime
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker1.name,
        )

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                any(),
                any(),
                any(),
            ),
        ).thenReturn(0.5)

        toTest.attemptFirePixel()

        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 0.5,
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now)
    }

    @Test
    fun whenLessThan24HoursPassedThenShouldNotFirePixel() = runTest {
        val startDate = baseTime
        val now = baseTime + oneHour // Only 1 hour passed

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(startDate)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        toTest.attemptFirePixel()

        verify(mockPirRepository, never()).getAllActiveBrokerObjects()
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(any(), any())
        verify(mockPirRepository, never()).setCustomStatsPixelsLastSentMs(any())
    }

    @Test
    fun whenMoreThan24HoursPassedThenShouldFirePixel() = runTest {
        val startDate = baseTime
        val now = baseTime + twentyFourHours + oneHour // 25 hours passed
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker1.name,
        )

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(startDate)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                any(),
                any(),
                any(),
            ),
        ).thenReturn(0.75)

        toTest.attemptFirePixel()

        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 0.75,
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now)
    }

    @Test
    fun whenExactly24HoursPassedThenShouldNotFirePixel() = runTest {
        val startDate = baseTime
        val now = baseTime + twentyFourHours // Exactly 24 hours

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(startDate)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)

        toTest.attemptFirePixel()

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

        toTest.attemptFirePixel()

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
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())

        toTest.attemptFirePixel()

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
        val jobRecord1 = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker1.name,
        )
        val jobRecord2 = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBroker2.name,
        )

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(
            listOf(
                testBroker1,
                testBroker2,
            ),
        )
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord1, jobRecord2))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                eq(listOf(jobRecord1)),
                eq(0L),
                eq(now - twentyFourHours),
            ),
        )
            .thenReturn(0.5)
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                eq(listOf(jobRecord2)),
                eq(0L),
                eq(now - twentyFourHours),
            ),
        )
            .thenReturn(0.8)

        toTest.attemptFirePixel()

        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 0.5,
        )
        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker2.url,
            optOutSuccessRate = 0.8,
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now)
    }

    @Test
    fun whenSuccessRateIsNullThenShouldNotFirePixelForThatBroker() = runTest {
        val now = baseTime
        val jobRecord1 = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker1.name,
        )
        val jobRecord2 = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBroker2.name,
        )

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(
            listOf(
                testBroker1,
                testBroker2,
            ),
        )
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord1, jobRecord2))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                eq(listOf(jobRecord1)),
                eq(0L),
                eq(now - twentyFourHours),
            ),
        )
            .thenReturn(0.5)
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                eq(listOf(jobRecord2)),
                eq(0L),
                eq(now - twentyFourHours),
            ),
        )
            .thenReturn(null)

        toTest.attemptFirePixel()

        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 0.5,
        )
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = eq(testBroker2.url),
            optOutSuccessRate = any(),
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now)
    }

    @Test
    fun whenAllSuccessRatesAreNullThenShouldNotFireAnyPixels() = runTest {
        val now = baseTime
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker1.name,
        )

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                any(),
                any(),
                any(),
            ),
        ).thenReturn(null)

        toTest.attemptFirePixel()

        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(any(), any())
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now)
    }

    @Test
    fun whenShouldFirePixelThenUsesCorrectDateRange() = runTest {
        val lastSentTime = baseTime
        val now = baseTime + twentyFourHours + oneHour
        // startDate is derived from lastSentMs: lastSentMs - 24h
        val expectedStartDate = lastSentTime - twentyFourHours
        val expectedEndDate = now - twentyFourHours
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker1.name,
        )

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(lastSentTime)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                any(),
                any(),
                any(),
            ),
        ).thenReturn(0.5)

        toTest.attemptFirePixel()

        // startDate = lastSentMs - 24h (the previous endDate)
        verify(mockOptOutSubmitRateCalculator).calculateOptOutSubmitRate(
            allActiveOptOutJobsForBroker = eq(listOf(jobRecord)),
            startDateMs = eq(expectedStartDate),
            endDateMs = eq(expectedEndDate),
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now)
    }

    @Test
    fun whenMultipleBrokersWithMixedSuccessRatesThenFiresPixelsForNonNullRates() = runTest {
        val now = baseTime
        val jobRecord1 = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker1.name,
        )
        val jobRecord2 = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBroker2.name,
        )

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(
            listOf(
                testBroker1,
                testBroker2,
            ),
        )
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord1, jobRecord2))
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                eq(listOf(jobRecord1)),
                eq(0L),
                eq(now - twentyFourHours),
            ),
        )
            .thenReturn(null)
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                eq(listOf(jobRecord2)),
                eq(0L),
                eq(now - twentyFourHours),
            ),
        )
            .thenReturn(0.9)

        toTest.attemptFirePixel()

        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = eq(testBroker1.url),
            optOutSuccessRate = any(),
        )
        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker2.url,
            optOutSuccessRate = 0.9,
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now)
    }

    @Test
    fun whenShouldFirePixelButNoBrokersAndNoProfilesThenReturnsSuccess() = runTest {
        val now = baseTime
        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(emptyList())

        toTest.attemptFirePixel()

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

        toTest.attemptFirePixel()

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
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())

        toTest.attemptFirePixel()

        verify(mockOptOutSubmitRateCalculator, never()).calculateOptOutSubmitRate(
            any(),
            any(),
            any(),
        )
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(any(), any())
        verify(mockPirRepository, never()).setCustomStatsPixelsLastSentMs(any())
    }

    @Test
    fun whenBrokerHasNoJobRecordsThenSkipsThatBroker() = runTest {
        val now = baseTime
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker1.name,
        )

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(
            listOf(
                testBroker1,
                testBroker2, // This broker has no job records
            ),
        )
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord)) // Only for broker1
        whenever(
            mockOptOutSubmitRateCalculator.calculateOptOutSubmitRate(
                eq(listOf(jobRecord)),
                eq(0L),
                eq(now - twentyFourHours),
            ),
        ).thenReturn(0.5)

        toTest.attemptFirePixel()

        // Only broker1 should fire pixel, broker2 should be skipped
        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 0.5,
        )
        verify(mockPirPixelSender, never()).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = eq(testBroker2.url),
            optOutSuccessRate = any(),
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now)
    }

    @Test
    fun whenExactly24HoursPassedSinceLastSentThenRecordsInDerivedWindowAreIncluded() = runTest {
        val lastSentTime = baseTime
        val now = baseTime + twentyFourHours + 1 // Just over 24 hours to pass shouldFirePixel check
        // startDate = lastSentTime - 24h, endDate = now - 24h

        // Record created 1 hour after the derived startDate - should be included
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker1.name,
            dateCreatedInMillis = (lastSentTime - twentyFourHours) + oneHour, // Within the window
            status = OptOutJobStatus.REQUESTED,
            optOutRequestedDateInMillis = (lastSentTime - twentyFourHours) + 2 * oneHour, // Processed within 24h
        )

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(lastSentTime)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))

        val realCalculator = RealOptOutSubmitRateCalculator(coroutineRule.testDispatcherProvider)
        val toTestWithRealCalculator = RealOptOut24HourSubmissionSuccessRateReporter(
            optOutSubmitRateCalculator = realCalculator,
            pirRepository = mockPirRepository,
            currentTimeProvider = mockCurrentTimeProvider,
            pirPixelSender = mockPirPixelSender,
            pirSchedulingRepository = mockSchedulingRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

        toTestWithRealCalculator.attemptFirePixel()

        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 1.0,
        )
    }

    @Test
    fun when25HoursPassedSinceLastSentThenRecordsCreatedThroughoutIntervalAreIncluded() = runTest {
        val lastSentTime = baseTime
        val now = baseTime + twentyFourHours + oneHour // 25 hours passed
        val derivedStartDate = lastSentTime - twentyFourHours

        // Record created 2 hours after derived startDate - included in the wider window
        val jobRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker1.name,
            dateCreatedInMillis = derivedStartDate + 2 * oneHour, // Within the window
            status = OptOutJobStatus.REQUESTED,
            optOutRequestedDateInMillis = derivedStartDate + 3 * oneHour, // Processed within 24h
        )

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(lastSentTime)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(jobRecord))

        val realCalculator = RealOptOutSubmitRateCalculator(coroutineRule.testDispatcherProvider)
        val toTestWithRealCalculator = RealOptOut24HourSubmissionSuccessRateReporter(
            optOutSubmitRateCalculator = realCalculator,
            pirRepository = mockPirRepository,
            currentTimeProvider = mockCurrentTimeProvider,
            pirPixelSender = mockPirPixelSender,
            pirSchedulingRepository = mockSchedulingRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

        toTestWithRealCalculator.attemptFirePixel()

        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 1.0,
        )
    }

    @Test
    fun when48HoursPassedSinceLastSentThenAllRecordsInExtendedWindowAreIncluded() = runTest {
        val lastSentTime = baseTime
        val now = baseTime + 2 * twentyFourHours // 48 hours passed
        val derivedStartDate = lastSentTime - twentyFourHours

        // Record created 25 hours after derived startDate - included in the window
        val jobRecordLater = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker1.name,
            dateCreatedInMillis = derivedStartDate + twentyFourHours + oneHour, // Within the extended window
            status = OptOutJobStatus.REQUESTED,
            optOutRequestedDateInMillis = derivedStartDate + twentyFourHours + 2 * oneHour,
        )

        // Record created shortly after derived startDate
        val jobRecordEarlier = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBroker1.name,
            dateCreatedInMillis = derivedStartDate + oneHour, // Also within window
            status = OptOutJobStatus.REQUESTED,
            optOutRequestedDateInMillis = derivedStartDate + 2 * oneHour,
        )

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(lastSentTime)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(
            listOf(jobRecordLater, jobRecordEarlier),
        )

        val realCalculator = RealOptOutSubmitRateCalculator(coroutineRule.testDispatcherProvider)
        val toTestWithRealCalculator = RealOptOut24HourSubmissionSuccessRateReporter(
            optOutSubmitRateCalculator = realCalculator,
            pirRepository = mockPirRepository,
            currentTimeProvider = mockCurrentTimeProvider,
            pirPixelSender = mockPirPixelSender,
            pirSchedulingRepository = mockSchedulingRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

        toTestWithRealCalculator.attemptFirePixel()

        // Both records should be counted
        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 1.0,
        )
        verify(mockPirRepository).setCustomStatsPixelsLastSentMs(now)
    }

    @Test
    fun whenConsecutive24HourRunsThenSecondRunCapturesRecordsFromPreviousWindow() = runTest {
        // First run at T stored lastSentMs = T
        // Second run: now = T + 25h
        // startDate = T - 24h, endDate = T + 1h
        // Window: (T - 24h) to (T + 1h) = 25 hour window
        //
        // Note: Records created AFTER (T + 1h) won't be in this window because
        // endDate = now - 24h ensures records have had 24 hours to be processed.
        // Those records will be captured in the NEXT run.
        val lastSentTime = baseTime
        val secondRunTime = baseTime + twentyFourHours + oneHour
        val derivedStartDate = lastSentTime - twentyFourHours

        // Record created within the valid window (between T - 24h and T + 1h)
        // This record was created before the first run but after the previous endDate
        val recordInWindow = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker1.name,
            dateCreatedInMillis = derivedStartDate + 12 * oneHour, // T - 12h, within the window
            status = OptOutJobStatus.REQUESTED,
            optOutRequestedDateInMillis = derivedStartDate + 13 * oneHour, // Processed within 24h of creation
        )

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(lastSentTime)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(secondRunTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(recordInWindow))

        val realCalculator = RealOptOutSubmitRateCalculator(coroutineRule.testDispatcherProvider)
        val toTestWithRealCalculator = RealOptOut24HourSubmissionSuccessRateReporter(
            optOutSubmitRateCalculator = realCalculator,
            pirRepository = mockPirRepository,
            currentTimeProvider = mockCurrentTimeProvider,
            pirPixelSender = mockPirPixelSender,
            pirSchedulingRepository = mockSchedulingRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

        toTestWithRealCalculator.attemptFirePixel()

        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 1.0,
        )
    }

    @Test
    fun whenMultipleRecordsCreatedOverTimeThenAllAreIncludedInWideWindow() = runTest {
        val lastSentTime = baseTime
        val now = baseTime + twentyFourHours + oneHour // 25 hours passed
        val derivedStartDate = lastSentTime - twentyFourHours
        // Window is now: (T - 24h) to (T + 1h) = 25 hours

        // Create records at various times - all within the window
        val recordEarly = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBroker1.name,
            dateCreatedInMillis = derivedStartDate + 30 * 60 * 1000L, // 30 min after startDate
            status = OptOutJobStatus.REQUESTED,
            optOutRequestedDateInMillis = derivedStartDate + oneHour,
        )
        val recordMidWindow = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBroker1.name,
            dateCreatedInMillis = derivedStartDate + oneHour + 1,
            status = OptOutJobStatus.REQUESTED,
            optOutRequestedDateInMillis = derivedStartDate + 2 * oneHour,
        )
        val recordLaterInWindow = createOptOutJobRecord(
            extractedProfileId = 3L,
            brokerName = testBroker1.name,
            dateCreatedInMillis = derivedStartDate + 12 * oneHour, // 12 hours after startDate
            status = OptOutJobStatus.REQUESTED,
            optOutRequestedDateInMillis = derivedStartDate + 13 * oneHour,
        )
        val recordNearEndOfWindow = createOptOutJobRecord(
            extractedProfileId = 4L,
            brokerName = testBroker1.name,
            dateCreatedInMillis = derivedStartDate + 23 * oneHour, // 23 hours after startDate
            status = OptOutJobStatus.REQUESTED,
            optOutRequestedDateInMillis = derivedStartDate + twentyFourHours,
        )

        whenever(mockPirRepository.getCustomStatsPixelsLastSentMs()).thenReturn(lastSentTime)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(now)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker1))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(
            listOf(recordEarly, recordMidWindow, recordLaterInWindow, recordNearEndOfWindow),
        )

        val realCalculator = RealOptOutSubmitRateCalculator(coroutineRule.testDispatcherProvider)
        val toTestWithRealCalculator = RealOptOut24HourSubmissionSuccessRateReporter(
            optOutSubmitRateCalculator = realCalculator,
            pirRepository = mockPirRepository,
            currentTimeProvider = mockCurrentTimeProvider,
            pirPixelSender = mockPirPixelSender,
            pirSchedulingRepository = mockSchedulingRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

        toTestWithRealCalculator.attemptFirePixel()

        // All 4 records should be counted (100% success rate)
        verify(mockPirPixelSender).reportBrokerCustomStateOptOutSubmitRate(
            brokerUrl = testBroker1.url,
            optOutSuccessRate = 1.0,
        )
    }

    private fun createOptOutJobRecord(
        extractedProfileId: Long,
        brokerName: String = testBroker1.name,
        userProfileId: Long = 1L,
        status: OptOutJobStatus = OptOutJobStatus.NOT_EXECUTED,
        dateCreatedInMillis: Long = baseTime,
        optOutRequestedDateInMillis: Long = 0L,
        optOutRemovedDateInMillis: Long = 0L,
        attemptCount: Int = 0,
        lastOptOutAttemptDateInMillis: Long = 0L,
        deprecated: Boolean = false,
    ): OptOutJobRecord {
        return OptOutJobRecord(
            brokerName = brokerName,
            userProfileId = userProfileId,
            extractedProfileId = extractedProfileId,
            status = status,
            attemptCount = attemptCount,
            lastOptOutAttemptDateInMillis = lastOptOutAttemptDateInMillis,
            optOutRequestedDateInMillis = optOutRequestedDateInMillis,
            optOutRemovedDateInMillis = optOutRemovedDateInMillis,
            deprecated = deprecated,
            dateCreatedInMillis = dateCreatedInMillis,
        )
    }
}
