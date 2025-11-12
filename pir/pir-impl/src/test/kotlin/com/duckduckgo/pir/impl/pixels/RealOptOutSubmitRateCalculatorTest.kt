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
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class RealOptOutSubmitRateCalculatorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealOptOutSubmitRateCalculator

    private val mockSchedulingRepository: PirSchedulingRepository = mock()

    @Before
    fun setUp() {
        testee = RealOptOutSubmitRateCalculator(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            schedulingRepository = mockSchedulingRepository,
        )
    }

    // Test data
    private val testBrokerName = "test-broker"
    private val testBrokerName2 = "test-broker-2"

    // January 15, 2024 10:00:00 UTC
    private val baseTime = 1705309200000L
    private val oneHour = TimeUnit.HOURS.toMillis(1)
    private val oneDay = TimeUnit.DAYS.toMillis(1)
    private val twentyFourHours = TimeUnit.HOURS.toMillis(24)

    @Test
    fun whenNoRecordsInDateRangeThenReturnNull() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(emptyList())

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertNull(result)
    }

    @Test
    fun whenRecordsExistButNoneInDateRangeThenReturnNull() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay

        val recordBeforeRange = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            dateCreatedInMillis = baseTime - oneDay,
        )
        val recordAfterRange = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            dateCreatedInMillis = baseTime + oneDay + oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(recordBeforeRange, recordAfterRange))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertNull(result)
    }

    @Test
    fun whenRecordsInDateRangeButNoneRequestedThenReturnZero() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay

        val record1 = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.NOT_EXECUTED,
            dateCreatedInMillis = baseTime + oneHour,
        )
        val record2 = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.ERROR,
            dateCreatedInMillis = baseTime + 2 * oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(record1, record2))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertEquals(0.0, result!!, 0.0)
    }

    @Test
    fun whenAllRecordsInDateRangeAreRequestedThenReturnOne() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        val record1 = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneHour, // Within 24 hours
        )
        val record2 = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
            optOutRequestedDateInMillis = dateCreated + 3 * oneHour, // Within 24 hours
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(record1, record2))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertEquals(1.0, result!!, 0.0)
    }

    @Test
    fun whenHalfRecordsInDateRangeAreRequestedThenReturnHalf() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        val requestedRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneHour, // Within 24 hours
        )
        val notExecutedRecord = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.NOT_EXECUTED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requestedRecord, notExecutedRecord))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertEquals(0.5, result!!, 0.0)
    }

    @Test
    fun whenRequestedRecordOutside24HourWindowThenNotCounted() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        val requestedWithinWindow = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneHour, // Within 24 hours
        )
        val requestedOutsideWindow = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
            optOutRequestedDateInMillis = dateCreated + 2 * oneHour + twentyFourHours + oneHour, // Outside 24 hours
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requestedWithinWindow, requestedOutsideWindow))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertEquals(0.5, result!!, 0.0)
    }

    @Test
    fun whenRequestedRecordExactlyAt24HourWindowThenCounted() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        val requestedAt24Hours = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + twentyFourHours, // Exactly 24 hours
        )
        val notExecutedRecord = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.NOT_EXECUTED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requestedAt24Hours, notExecutedRecord))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertEquals(0.5, result!!, 0.0)
    }

    @Test
    fun whenRecordsFromDifferentBrokersThenOnlyCountMatchingBroker() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        val recordForTestBroker = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneHour,
        )
        val recordForOtherBroker = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName2,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
            optOutRequestedDateInMillis = dateCreated + 3 * oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(recordForTestBroker))
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName2))
            .thenReturn(listOf(recordForOtherBroker))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertEquals(1.0, result!!, 0.0)
    }

    @Test
    fun whenStartDateIsZeroThenUseDefaultStartDate() = runTest {
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        val record = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(record))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, endDateMs = endDate)

        assertEquals(1.0, result!!, 0.0)
    }

    @Test
    fun whenRecordAtStartDateBoundaryThenIncluded() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay

        val recordAtStart = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = startDate, // Exactly at start
            optOutRequestedDateInMillis = startDate + oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(recordAtStart))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertEquals(1.0, result!!, 0.0)
    }

    @Test
    fun whenRecordAtEndDateBoundaryThenIncluded() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay

        val recordAtEnd = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = endDate, // Exactly at end
            optOutRequestedDateInMillis = endDate + oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(recordAtEnd))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertEquals(1.0, result!!, 0.0)
    }

    @Test
    fun whenMultipleStatusesThenOnlyRequestedCounted() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        val requested = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneHour,
        )
        val removed = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REMOVED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
        )
        val error = createOptOutJobRecord(
            extractedProfileId = 3L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.ERROR,
            dateCreatedInMillis = dateCreated + 3 * oneHour,
        )
        val pendingEmail = createOptOutJobRecord(
            extractedProfileId = 4L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.PENDING_EMAIL_CONFIRMATION,
            dateCreatedInMillis = dateCreated + 4 * oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requested, removed, error, pendingEmail))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertEquals(0.25, result!!, 0.0)
    }

    @Test
    fun whenComplexScenarioThenCalculateCorrectly() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        // 5 records in range
        val requested1 = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneHour, // Within 24h
        )
        val requested2 = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
            optOutRequestedDateInMillis = dateCreated + 2 * oneHour + twentyFourHours + oneHour, // Outside 24h
        )
        val requested3 = createOptOutJobRecord(
            extractedProfileId = 3L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated + 3 * oneHour,
            optOutRequestedDateInMillis = dateCreated + 3 * oneHour + twentyFourHours, // Exactly 24h
        )
        val notExecuted = createOptOutJobRecord(
            extractedProfileId = 4L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.NOT_EXECUTED,
            dateCreatedInMillis = dateCreated + 4 * oneHour,
        )
        val error = createOptOutJobRecord(
            extractedProfileId = 5L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.ERROR,
            dateCreatedInMillis = dateCreated + 5 * oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requested1, requested2, requested3, notExecuted, error))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        // Only requested1 and requested3 count (2 out of 5)
        assertEquals(0.4, result!!, 0.0)
    }

    @Test
    fun whenOptOutRequestedDateEqualsDateCreatedThenNotCounted() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        val requestedAtSameTime = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated, // Exactly equal (should not be counted)
        )
        val validRequested = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
            optOutRequestedDateInMillis = dateCreated + 2 * oneHour + oneHour, // After creation
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requestedAtSameTime, validRequested))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        // Only validRequested counts (1 out of 2)
        assertEquals(0.5, result!!, 0.0)
    }

    @Test
    fun whenOptOutRequestedDateBeforeDateCreatedThenNotCounted() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        val requestedBeforeCreation = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated - oneHour, // Before creation (should not be counted)
        )
        val validRequested = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
            optOutRequestedDateInMillis = dateCreated + 3 * oneHour, // After creation
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requestedBeforeCreation, validRequested))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        // Only validRequested counts (1 out of 2)
        assertEquals(0.5, result!!, 0.0)
    }

    @Test
    fun whenOptOutRequestedDateJustAfterDateCreatedThenCounted() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour
        val oneMillisecond = 1L

        val requestedJustAfter = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneMillisecond, // Just 1ms after (should be counted)
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requestedJustAfter))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertEquals(1.0, result!!, 0.0)
    }

    @Test
    fun whenOptOutRequestedDateJustBefore24HourLimitThenCounted() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour
        val oneMillisecond = 1L

        val requestedJustBefore24h = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + twentyFourHours - oneMillisecond, // Just before 24h limit
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requestedJustBefore24h))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertEquals(1.0, result!!, 0.0)
    }

    @Test
    fun whenOptOutRequestedDateJustAfter24HourLimitThenNotCounted() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour
        val oneMillisecond = 1L

        val requestedJustAfter24h = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + twentyFourHours + oneMillisecond, // Just after 24h limit
        )
        val notExecuted = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.NOT_EXECUTED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requestedJustAfter24h, notExecuted))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        // requestedJustAfter24h doesn't count, so 0 out of 2
        assertEquals(0.0, result!!, 0.0)
    }

    @Test
    fun whenResultNeedsRoundingThenRoundsToTwoDecimalPlaces() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        // 1 out of 3 = 0.333... should round to 0.33
        val requested = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneHour,
        )
        val notExecuted1 = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.NOT_EXECUTED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
        )
        val notExecuted2 = createOptOutJobRecord(
            extractedProfileId = 3L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.NOT_EXECUTED,
            dateCreatedInMillis = dateCreated + 3 * oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requested, notExecuted1, notExecuted2))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        // 1/3 = 0.333... rounded to 0.33
        assertEquals(0.33, result!!, 0.0)
    }

    @Test
    fun whenResultNeedsRoundingUpThenRoundsCorrectly() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        // 2 out of 3 = 0.666... should round to 0.67
        val requested1 = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneHour,
        )
        val requested2 = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
            optOutRequestedDateInMillis = dateCreated + 3 * oneHour,
        )
        val notExecuted = createOptOutJobRecord(
            extractedProfileId = 3L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.NOT_EXECUTED,
            dateCreatedInMillis = dateCreated + 4 * oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requested1, requested2, notExecuted))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        // 2/3 = 0.666... rounded to 0.67
        assertEquals(0.67, result!!, 0.0)
    }

    @Test
    fun whenRepositoryReturnsWrongBrokerNameThenExcluded() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        // Repository might return records with wrong broker name (should be filtered out)
        val recordWithWrongBroker = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName2, // Wrong broker
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneHour,
        )
        val recordWithCorrectBroker = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName, // Correct broker
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
            optOutRequestedDateInMillis = dateCreated + 3 * oneHour,
        )

        // Repository returns both, but only correct broker should be counted
        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(recordWithWrongBroker, recordWithCorrectBroker))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        // Only recordWithCorrectBroker counts (1 out of 1 after filtering)
        assertEquals(1.0, result!!, 0.0)
    }

    @Test
    fun whenSingleRecordRequestedThenReturnOne() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        val singleRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(singleRecord))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertEquals(1.0, result!!, 0.0)
    }

    @Test
    fun whenSingleRecordNotRequestedThenReturnZero() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        val singleRecord = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.NOT_EXECUTED,
            dateCreatedInMillis = dateCreated,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(singleRecord))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        assertEquals(0.0, result!!, 0.0)
    }

    @Test
    fun whenLargeDateRangeThenFiltersCorrectly() = runTest {
        val startDate = baseTime
        val endDate = baseTime + TimeUnit.DAYS.toMillis(365) // 1 year
        val dateCreated = baseTime + oneDay

        val record1 = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneHour,
        )
        val record2 = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated + TimeUnit.DAYS.toMillis(180), // 6 months later
            optOutRequestedDateInMillis = dateCreated + TimeUnit.DAYS.toMillis(180) + oneHour,
        )
        val record3 = createOptOutJobRecord(
            extractedProfileId = 3L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.NOT_EXECUTED,
            dateCreatedInMillis = dateCreated + TimeUnit.DAYS.toMillis(300), // 10 months later
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(record1, record2, record3))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        // 2 out of 3
        assertEquals(0.67, result!!, 0.0)
    }

    @Test
    fun whenRequestedRecordHasZeroOptOutRequestedDateThenNotCounted() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        val requestedWithZeroDate = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = 0L, // Zero (should not be counted)
        )
        val validRequested = createOptOutJobRecord(
            extractedProfileId = 2L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated + 2 * oneHour,
            optOutRequestedDateInMillis = dateCreated + 3 * oneHour,
        )

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requestedWithZeroDate, validRequested))

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        // Only validRequested counts (1 out of 2)
        assertEquals(0.5, result!!, 0.0)
    }

    @Test
    fun whenFractionalResultRoundsDownThenRoundsCorrectly() = runTest {
        val startDate = baseTime
        val endDate = baseTime + oneDay
        val dateCreated = baseTime + oneHour

        // 1 out of 7 = 0.142857... should round to 0.14
        val requested = createOptOutJobRecord(
            extractedProfileId = 1L,
            brokerName = testBrokerName,
            status = OptOutJobStatus.REQUESTED,
            dateCreatedInMillis = dateCreated,
            optOutRequestedDateInMillis = dateCreated + oneHour,
        )
        val notExecutedRecords = (2L..7L).map { id ->
            createOptOutJobRecord(
                extractedProfileId = id,
                brokerName = testBrokerName,
                status = OptOutJobStatus.NOT_EXECUTED,
                dateCreatedInMillis = dateCreated + id * oneHour,
            )
        }

        whenever(mockSchedulingRepository.getAllValidOptOutJobRecordsForBroker(testBrokerName))
            .thenReturn(listOf(requested) + notExecutedRecords)

        val result = testee.calculateOptOutSubmitRate(testBrokerName, startDate, endDate)

        // 1/7 = 0.142857... rounded to 0.14
        assertEquals(0.14, result!!, 0.0)
    }

    private fun createOptOutJobRecord(
        extractedProfileId: Long,
        brokerName: String = testBrokerName,
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
