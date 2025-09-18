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

package com.duckduckgo.pir.impl.scheduling

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.models.scheduling.BrokerSchedulingConfig
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.TimeUnit.HOURS
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealEligibleOptOutJobProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealEligibleOptOutJobProvider

    private val mockPirSchedulingRepository: PirSchedulingRepository = mock()
    private val mockPirRepository: PirRepository = mock()

    @Before
    fun setUp() {
        testee = RealEligibleOptOutJobProvider(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pirSchedulingRepository = mockPirSchedulingRepository,
            pirRepository = mockPirRepository,
        )
    }

    // Test data
    private val currentTimeMillis = 1753270157085L

    private val brokerSchedulingConfig = BrokerSchedulingConfig(
        brokerName = "test-broker",
        retryErrorInMillis = HOURS.toMillis(1),
        confirmOptOutScanInMillis = HOURS.toMillis(24),
        maintenanceScanInMillis = DAYS.toMillis(7),
        maxAttempts = 3,
    )

    private val anotherBrokerSchedulingConfig = BrokerSchedulingConfig(
        brokerName = "another-broker",
        retryErrorInMillis = HOURS.toMillis(2),
        confirmOptOutScanInMillis = HOURS.toMillis(48),
        maintenanceScanInMillis = DAYS.toMillis(14),
        maxAttempts = 5,
    )

    private val optOutJobRecordNotExecuted = OptOutJobRecord(
        extractedProfileId = 789L,
        brokerName = "test-broker",
        userProfileId = 123L,
        status = OptOutJobStatus.NOT_EXECUTED,
        attemptCount = 0,
        lastOptOutAttemptDateInMillis = 0L,
        optOutRequestedDateInMillis = 0L,
        optOutRemovedDateInMillis = 0L,
    )

    private val optOutJobRecordError = OptOutJobRecord(
        extractedProfileId = 790L,
        brokerName = "test-broker",
        userProfileId = 124L,
        status = OptOutJobStatus.ERROR,
        attemptCount = 1,
        lastOptOutAttemptDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(2), // 2 hours ago, should trigger retry
        optOutRequestedDateInMillis = 0L,
        optOutRemovedDateInMillis = 0L,
    )

    private val optOutJobRecordRequestedForReRequest = OptOutJobRecord(
        extractedProfileId = 791L,
        brokerName = "test-broker",
        userProfileId = 125L,
        status = OptOutJobStatus.REQUESTED,
        attemptCount = 2,
        lastOptOutAttemptDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(1),
        optOutRequestedDateInMillis = currentTimeMillis - TimeUnit.DAYS.toMillis(29), // 29 days ago, should trigger re-request
        optOutRemovedDateInMillis = 0L,
    )

    private val optOutJobRecordRequestedTooManyAttempts = OptOutJobRecord(
        extractedProfileId = 792L,
        brokerName = "test-broker",
        userProfileId = 126L,
        status = OptOutJobStatus.REQUESTED,
        attemptCount = 4, // Exceeds maxAttempts (3)
        lastOptOutAttemptDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(1),
        optOutRequestedDateInMillis = currentTimeMillis - TimeUnit.DAYS.toMillis(29),
        optOutRemovedDateInMillis = 0L,
    )

    private val optOutJobRecordRequestedNotTimeForReRequest = OptOutJobRecord(
        extractedProfileId = 793L,
        brokerName = "test-broker",
        userProfileId = 127L,
        status = OptOutJobStatus.REQUESTED,
        attemptCount = 1,
        lastOptOutAttemptDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(1),
        optOutRequestedDateInMillis = currentTimeMillis - TimeUnit.DAYS.toMillis(10), // 10 days ago, NOT time for re-request
        optOutRemovedDateInMillis = 0L,
    )

    private val optOutJobRecordRemoved = OptOutJobRecord(
        extractedProfileId = 794L,
        brokerName = "test-broker",
        userProfileId = 128L,
        status = OptOutJobStatus.REMOVED,
        attemptCount = 1,
        lastOptOutAttemptDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(1),
        optOutRequestedDateInMillis = currentTimeMillis - TimeUnit.DAYS.toMillis(5),
        optOutRemovedDateInMillis = currentTimeMillis - TimeUnit.DAYS.toMillis(1),
    )

    private val optOutJobRecordErrorNotTimeForRetry = OptOutJobRecord(
        extractedProfileId = 795L,
        brokerName = "test-broker",
        userProfileId = 129L,
        status = OptOutJobStatus.ERROR,
        attemptCount = 1,
        lastOptOutAttemptDateInMillis = currentTimeMillis - TimeUnit.MINUTES.toMillis(30), // 30 minutes ago, NOT time for retry
        optOutRequestedDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(2),
        optOutRemovedDateInMillis = 0L,
    )

    @Test
    fun whenGetAllEligibleOptOutJobsAndNoSchedulingConfigThenReturnEmpty() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(optOutJobRecordNotExecuted))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithNotExecutedJobThenReturnRecord() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(optOutJobRecordNotExecuted))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertEquals(1, result.size)
        assertEquals(optOutJobRecordNotExecuted, result[0])
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithErrorJobDueForRetryThenReturnRecord() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(optOutJobRecordError))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertEquals(1, result.size)
        assertEquals(optOutJobRecordError, result[0])
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithErrorJobNotTimeForRetryThenReturnEmpty() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(optOutJobRecordErrorNotTimeForRetry))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithRequestedJobDueForReRequestThenReturnRecord() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(optOutJobRecordRequestedForReRequest))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertEquals(1, result.size)
        assertEquals(optOutJobRecordRequestedForReRequest, result[0])
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithRequestedJobTooManyAttemptsThenReturnEmpty() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(optOutJobRecordRequestedTooManyAttempts))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithRequestedJobNotTimeForReRequestThenReturnEmpty() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(optOutJobRecordRequestedNotTimeForReRequest))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithRemovedJobNotValidThenReturnEmpty() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(optOutJobRecordRemoved))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithMixedRecordsThenReturnOnlyEligible() = runTest {
        val allOptOutRecords = listOf(
            optOutJobRecordNotExecuted, // Should be eligible
            optOutJobRecordError, // Should be eligible (due for retry)
            optOutJobRecordRequestedForReRequest, // Should be eligible
            optOutJobRecordRequestedTooManyAttempts, // Should NOT be eligible (too many attempts)
            optOutJobRecordRequestedNotTimeForReRequest, // Should NOT be eligible (not time yet)
            optOutJobRecordRemoved, // Should NOT be eligible (removed status)
            optOutJobRecordErrorNotTimeForRetry, // Should NOT be eligible (not time for retry)
        )

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(allOptOutRecords)

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertEquals(3, result.size)
        assertTrue(result.contains(optOutJobRecordNotExecuted))
        assertTrue(result.contains(optOutJobRecordError))
        assertTrue(result.contains(optOutJobRecordRequestedForReRequest))
        assertTrue(!result.contains(optOutJobRecordRequestedTooManyAttempts))
        assertTrue(!result.contains(optOutJobRecordRequestedNotTimeForReRequest))
        assertTrue(!result.contains(optOutJobRecordRemoved))
        assertTrue(!result.contains(optOutJobRecordErrorNotTimeForRetry))
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithMultipleBrokersThenReturnOnlyMatchingBrokers() = runTest {
        val anotherBrokerOptOutRecord = optOutJobRecordNotExecuted.copy(
            extractedProfileId = 800L,
            brokerName = "another-broker",
            userProfileId = 200L,
        )
        val unknownBrokerOptOutRecord = optOutJobRecordNotExecuted.copy(
            extractedProfileId = 801L,
            brokerName = "unknown-broker",
            userProfileId = 300L,
        )

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(
            listOf(brokerSchedulingConfig, anotherBrokerSchedulingConfig),
        )
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(
            listOf(optOutJobRecordNotExecuted, anotherBrokerOptOutRecord, unknownBrokerOptOutRecord),
        )

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertEquals(2, result.size) // Should not include unknown-broker
        assertTrue(result.contains(optOutJobRecordNotExecuted))
        assertTrue(result.contains(anotherBrokerOptOutRecord))
        assertFalse(result.contains(unknownBrokerOptOutRecord))
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithResultsSortedByAttemptCountThenReturnSortedResults() = runTest {
        val record1 = optOutJobRecordNotExecuted.copy(attemptCount = 3, extractedProfileId = 801L)
        val record2 = optOutJobRecordNotExecuted.copy(attemptCount = 1, extractedProfileId = 802L)
        val record3 = optOutJobRecordNotExecuted.copy(attemptCount = 2, extractedProfileId = 803L)

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(record1, record2, record3))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertEquals(3, result.size)
        assertEquals(1, result[0].attemptCount) // Should be sorted by attemptCount
        assertEquals(2, result[1].attemptCount)
        assertEquals(3, result[2].attemptCount)
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithInvalidStatusThenReturnEmpty() = runTest {
        val invalidOptOutRecord = optOutJobRecordNotExecuted.copy(status = OptOutJobStatus.INVALID)
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(invalidOptOutRecord))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithErrorJobWithNullLastAttemptDateThenReturnEmpty() = runTest {
        val errorRecordWithNullDate = optOutJobRecordError.copy(lastOptOutAttemptDateInMillis = 0L)
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(errorRecordWithNullDate))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithRequestedJobWithNullLastAttemptDateThenReturnEmpty() = runTest {
        val requestedRecordWithNullDate = optOutJobRecordRequestedForReRequest.copy(optOutRequestedDateInMillis = 0L)
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(requestedRecordWithNullDate))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    // Edge cases for timing boundaries
    @Test
    fun whenGetAllEligibleOptOutJobsWithExactTimingBoundariesThenReturnCorrectRecords() = runTest {
        // Test error record that's exactly at the boundary for retry
        val exactBoundaryErrorRecord = optOutJobRecordError.copy(
            lastOptOutAttemptDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(1), // Exactly 1 hour ago
        )

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(exactBoundaryErrorRecord))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertEquals(1, result.size) // Should be eligible at exact boundary
        assertEquals(exactBoundaryErrorRecord, result[0])
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithExactReRequestTimingBoundaryThenReturnCorrectRecords() = runTest {
        // Test requested record that's exactly at the boundary for re-request
        val exactBoundaryRequestedRecord = optOutJobRecordRequestedForReRequest.copy(
            optOutRequestedDateInMillis = currentTimeMillis - TimeUnit.DAYS.toMillis(28), // Exactly 28 days ago
        )

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(exactBoundaryRequestedRecord))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertEquals(1, result.size) // Should be eligible at exact boundary
        assertEquals(exactBoundaryRequestedRecord, result[0])
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithMaxAttemptsValueMinusOneThenIgnoreMaxAttempts() = runTest {
        val configWithUnlimitedAttempts = brokerSchedulingConfig.copy(maxAttempts = -1)
        val recordWithManyAttempts = optOutJobRecordRequestedForReRequest.copy(attemptCount = 10) // High attempt count

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(configWithUnlimitedAttempts))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(recordWithManyAttempts))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertEquals(1, result.size) // Should be eligible despite high attempt count
        assertEquals(recordWithManyAttempts, result[0])
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithMaxAttemptsEqualToAttemptCountThenReturnRecord() = runTest {
        val recordAtMaxAttempts = optOutJobRecordRequestedForReRequest.copy(attemptCount = 3) // Equal to maxAttempts

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(recordAtMaxAttempts))

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertEquals(1, result.size) // Should be eligible when equal to maxAttempts
        assertEquals(recordAtMaxAttempts, result[0])
    }

    @Test
    fun whenGetAllEligibleOptOutJobsWithDifferentBrokerConfigurationsThenApplyCorrectRules() = runTest {
        val anotherBrokerErrorRecord = optOutJobRecordError.copy(
            extractedProfileId = 900L,
            brokerName = "another-broker",
            userProfileId = 900L,
            lastOptOutAttemptDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(1), // Only 1 hour ago
        )

        // another-broker has retryErrorInMillis = 2 hours, so this should NOT be eligible yet
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(
            listOf(brokerSchedulingConfig, anotherBrokerSchedulingConfig),
        )
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(
            listOf(optOutJobRecordError, anotherBrokerErrorRecord),
        )

        val result = testee.getAllEligibleOptOutJobs(currentTimeMillis)

        assertEquals(1, result.size) // Only test-broker should be eligible
        assertEquals("test-broker", result[0].brokerName)
    }
}
