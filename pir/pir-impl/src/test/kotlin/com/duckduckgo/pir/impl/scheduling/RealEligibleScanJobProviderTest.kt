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
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.TimeUnit.HOURS

class RealEligibleScanJobProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealEligibleScanJobProvider

    private val mockPirSchedulingRepository: PirSchedulingRepository = mock()
    private val mockPirRepository: PirRepository = mock()

    @Before
    fun setUp() {
        testee = RealEligibleScanJobProvider(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pirSchedulingRepository = mockPirSchedulingRepository,
            pirRepository = mockPirRepository,
        )
    }

    // Test data
    private val currentTimeMillis = 1000000L

    private val brokerSchedulingConfig = BrokerSchedulingConfig(
        brokerName = "test-broker",
        retryErrorInMillis = HOURS.toMillis(1),
        confirmOptOutScanInMillis = HOURS.toMillis(24),
        maintenanceScanInMillis = DAYS.toMillis(7),
        maxAttempts = 3,
    )

    private val scanJobRecordNotExecuted = ScanJobRecord(
        brokerName = "test-broker",
        userProfileId = 123L,
        status = ScanJobStatus.NOT_EXECUTED,
        lastScanDateInMillis = 0L,
    )

    private val scanJobRecordNoMatch = ScanJobRecord(
        brokerName = "test-broker",
        userProfileId = 124L,
        status = ScanJobStatus.NO_MATCH_FOUND,
        lastScanDateInMillis = currentTimeMillis - TimeUnit.DAYS.toMillis(8), // 8 days ago, should trigger maintenance
    )

    private val scanJobRecordMatchFound = ScanJobRecord(
        brokerName = "test-broker",
        userProfileId = 125L,
        status = ScanJobStatus.MATCHES_FOUND,
        lastScanDateInMillis = currentTimeMillis - TimeUnit.DAYS.toMillis(8), // 8 days ago, should trigger maintenance
    )

    private val scanJobRecordError = ScanJobRecord(
        brokerName = "test-broker",
        userProfileId = 126L,
        status = ScanJobStatus.ERROR,
        lastScanDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(2), // 2 hours ago, should trigger retry
    )

    private val scanJobRecordRecentNoMatch = ScanJobRecord(
        brokerName = "test-broker",
        userProfileId = 127L,
        status = ScanJobStatus.NO_MATCH_FOUND,
        lastScanDateInMillis = currentTimeMillis - TimeUnit.DAYS.toMillis(3), // 3 days ago, should NOT trigger maintenance
    )

    private val optOutJobRecordRequested = OptOutJobRecord(
        extractedProfileId = 789L,
        brokerName = "test-broker",
        userProfileId = 123L,
        status = OptOutJobStatus.REQUESTED,
        attemptCount = 1,
        lastOptOutAttemptDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(1),
        optOutRequestedDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(25), // 25 hours ago, should trigger confirm
        optOutRemovedDateInMillis = 0L,
    )

    private val optOutJobRecordRemoved = OptOutJobRecord(
        extractedProfileId = 790L,
        brokerName = "test-broker",
        userProfileId = 124L,
        status = OptOutJobStatus.REMOVED,
        attemptCount = 1,
        lastOptOutAttemptDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(1),
        optOutRequestedDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(1),
        optOutRemovedDateInMillis = currentTimeMillis - TimeUnit.DAYS.toMillis(7), // 7 days ago, should trigger maintain
    )

    @Test
    fun whenGetAllEligibleScanJobsAndNoSchedulingConfigThenReturnEmpty() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(scanJobRecordNotExecuted))

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleScanJobsWithNotExecutedScanRecordThenReturnRecord() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(scanJobRecordNotExecuted))

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(1, result.size)
        assertEquals(scanJobRecordNotExecuted, result[0])
    }

    @Test
    fun whenGetAllEligibleScanJobsWithNotExecutedScanRecordWithNullLastScanDateThenReturnRecord() = runTest {
        val scanRecord = scanJobRecordNotExecuted.copy(lastScanDateInMillis = 0L)
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(scanRecord))

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(1, result.size)
        assertEquals(scanRecord, result[0])
    }

    @Test
    fun whenGetAllEligibleScanJobsWithNoMatchFoundAndDueForMaintenanceThenReturnRecord() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(scanJobRecordNoMatch))

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(1, result.size)
        assertEquals(scanJobRecordNoMatch, result[0])
    }

    @Test
    fun whenGetAllEligibleScanJobsWithMatchFoundAndDueForMaintenanceThenReturnRecord() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(scanJobRecordMatchFound))

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(1, result.size)
        assertEquals(scanJobRecordMatchFound, result[0])
    }

    @Test
    fun whenGetAllEligibleScanJobsWithErrorAndDueForRetryThenReturnRecord() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(scanJobRecordError))

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(1, result.size)
        assertEquals(scanJobRecordError, result[0])
    }

    @Test
    fun whenGetAllEligibleScanJobsWithRecentNoMatchNotDueForMaintenanceThenReturnEmpty() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(scanJobRecordRecentNoMatch))

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleScanJobsWithOptOutRequestedAndDueForConfirmThenReturnScanRecord() = runTest {
        val expectedScanRecord = scanJobRecordNotExecuted
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(optOutJobRecordRequested))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 123L)).thenReturn(expectedScanRecord)

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(1, result.size)
        assertEquals(expectedScanRecord, result[0])
    }

    @Test
    fun whenGetAllEligibleScanJobsWithOptOutRemovedAndDueForMaintenanceThenReturnScanRecord() = runTest {
        val expectedScanRecord = scanJobRecordNotExecuted.copy(userProfileId = 124L)
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(optOutJobRecordRemoved))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 124L)).thenReturn(expectedScanRecord)

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(1, result.size)
        assertEquals(expectedScanRecord, result[0])
    }

    @Test
    fun whenGetAllEligibleScanJobsWithOptOutButNoScanRecordThenFilterOut() = runTest {
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(optOutJobRecordRequested))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 123L)).thenReturn(null)

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleScanJobsWithMixedRecordsThenReturnAllEligible() = runTest {
        val allScanRecords = listOf(
            scanJobRecordNotExecuted,
            scanJobRecordNoMatch,
            scanJobRecordMatchFound,
            scanJobRecordError,
            scanJobRecordRecentNoMatch, // This one should NOT be eligible
        )
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(allScanRecords)

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(4, result.size) // All except scanJobRecordRecentNoMatch
        assertTrue(result.contains(scanJobRecordNotExecuted))
        assertTrue(result.contains(scanJobRecordNoMatch))
        assertTrue(result.contains(scanJobRecordMatchFound))
        assertTrue(result.contains(scanJobRecordError))
        assertTrue(!result.contains(scanJobRecordRecentNoMatch))
    }

    @Test
    fun whenGetAllEligibleScanJobsWithDuplicateRecordsFromOptOutAndScanThenReturnUniqueRecords() = runTest {
        val duplicateScanRecord = scanJobRecordNotExecuted
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(optOutJobRecordRequested))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(duplicateScanRecord))
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 123L)).thenReturn(duplicateScanRecord)

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(1, result.size) // Should deduplicate
        assertEquals(duplicateScanRecord, result[0])
    }

    @Test
    fun whenGetAllEligibleScanJobsWithMultipleBrokersThenReturnOnlyMatchingBrokers() = runTest {
        val anotherBrokerConfig = brokerSchedulingConfig.copy(brokerName = "another-broker")
        val anotherBrokerScanRecord = scanJobRecordNotExecuted.copy(brokerName = "another-broker", userProfileId = 200L)
        val unknownBrokerScanRecord = scanJobRecordNotExecuted.copy(brokerName = "unknown-broker", userProfileId = 300L)

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig, anotherBrokerConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(
            listOf(scanJobRecordNotExecuted, anotherBrokerScanRecord, unknownBrokerScanRecord),
        )

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(2, result.size) // Should not include unknown-broker
        assertTrue(result.any { it.brokerName == "test-broker" })
        assertTrue(result.any { it.brokerName == "another-broker" })
        assertTrue(result.none { it.brokerName == "unknown-broker" })
    }

    @Test
    fun whenGetAllEligibleScanJobsWithDeprecatedStatusThenReturnEmpty() = runTest {
        val invalidScanRecord = scanJobRecordNotExecuted.copy(
            deprecated = true,
            lastScanDateInMillis = 123L,
        )
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(invalidScanRecord))

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleScanJobsWithOptOutNotRequestedThenReturnEmpty() = runTest {
        val optOutNotRequested = optOutJobRecordRequested.copy(status = OptOutJobStatus.NOT_EXECUTED)
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(optOutNotRequested))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(emptyList())

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    // Edge cases for timing
    @Test
    fun whenGetAllEligibleScanJobsWithExactTimingBoundariesThenReturnCorrectRecords() = runTest {
        // Test record that's exactly at the boundary for maintenance scan
        val exactBoundaryScanRecord = scanJobRecordNoMatch.copy(
            lastScanDateInMillis = currentTimeMillis - TimeUnit.DAYS.toMillis(7), // Exactly 7 days ago
        )

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(exactBoundaryScanRecord))

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(1, result.size) // Should be eligible at exact boundary
        assertEquals(exactBoundaryScanRecord, result[0])
    }

    @Test
    fun whenGetAllEligibleScanJobsWithOptOutExactTimingBoundariesThenReturnCorrectRecords() = runTest {
        // Test opt-out record that's exactly at the boundary for confirmation scan
        val exactBoundaryOptOutRecord = optOutJobRecordRequested.copy(
            optOutRequestedDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(24), // Exactly 24 hours ago
        )
        val expectedScanRecord = scanJobRecordNotExecuted

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(exactBoundaryOptOutRecord))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 123L)).thenReturn(expectedScanRecord)

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(1, result.size) // Should be eligible at exact boundary
        assertEquals(expectedScanRecord, result[0])
    }

    @Test
    fun whenGetAllEligibleScanJobsWithMixedSourcesThenReturnCorrectOrderingAndNoDuplicates() = runTest {
        // Create opt-out records with different attempt counts
        val optOutRecord1 = optOutJobRecordRequested.copy(
            extractedProfileId = 1001L,
            userProfileId = 1001L,
            attemptCount = 3,
            optOutRequestedDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(25),
        )
        val optOutRecord2 = optOutJobRecordRequested.copy(
            extractedProfileId = 1002L,
            userProfileId = 1002L,
            attemptCount = 1,
            optOutRequestedDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(25),
        )
        val optOutRecord3 = optOutJobRecordRequested.copy(
            extractedProfileId = 1003L,
            userProfileId = 1003L,
            attemptCount = 2,
            optOutRequestedDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(25),
        )

        // Create scan records with different lastScanDateInMillis
        val scanRecord1 = scanJobRecordNoMatch.copy(
            userProfileId = 2001L,
            lastScanDateInMillis = currentTimeMillis - TimeUnit.DAYS.toMillis(10), // 10 days ago
        )
        val scanRecord2 = scanJobRecordMatchFound.copy(
            userProfileId = 2002L,
            lastScanDateInMillis = currentTimeMillis - TimeUnit.DAYS.toMillis(8), // 8 days ago
        )
        val scanRecord3 = scanJobRecordError.copy(
            userProfileId = 2003L,
            lastScanDateInMillis = currentTimeMillis - TimeUnit.HOURS.toMillis(2), // 2 hours ago
        )

        // Create corresponding scan records for opt-out records
        val scanFromOptOut1 = scanJobRecordNotExecuted.copy(userProfileId = 1001L)
        val scanFromOptOut2 = scanJobRecordNotExecuted.copy(userProfileId = 1002L)
        val scanFromOptOut3 = scanJobRecordNotExecuted.copy(userProfileId = 1003L)

        // Add a duplicate scan record that should be deduplicated
        val duplicateScanRecord = scanFromOptOut3.copy() // Same as scanRecord1

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(
            listOf(optOutRecord1, optOutRecord2, optOutRecord3),
        )
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(
            listOf(scanRecord1, scanRecord2, scanRecord3, duplicateScanRecord),
        )

        // Mock the scan record lookups for opt-out records
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 1001L)).thenReturn(scanFromOptOut1)
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 1002L)).thenReturn(scanFromOptOut2)
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 1003L)).thenReturn(scanFromOptOut3)

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        // Should have 6 unique records (3 from opt-out + 3 from scan records, with duplicate removed)
        assertEquals(6, result.size)

        // First 3 records should be from opt-out, sorted by attempt count (1, 2, 3)
        assertEquals(scanFromOptOut2, result[0]) // attemptCount = 1
        assertEquals(scanFromOptOut3, result[1]) // attemptCount = 2
        assertEquals(scanFromOptOut1, result[2]) // attemptCount = 3

        // Next 3 records should be from scan records, sorted by lastScanDateInMillis (oldest first)
        assertEquals(scanRecord1, result[3]) // 10 days ago
        assertEquals(scanRecord2, result[4]) // 8 days ago
        assertEquals(scanRecord3, result[5]) // 2 hours ago

        // Verify no duplicates exist
        val uniqueRecords = result.toSet()
        assertEquals(result.size, uniqueRecords.size)
    }

    @Test
    fun whenGetAllEligibleScanJobsWithDeprecatedScanRecordThenFilterOut() = runTest {
        val deprecatedScanRecord = scanJobRecordNotExecuted.copy(deprecated = true)
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(listOf(deprecatedScanRecord))

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleScanJobsWithMixOfDeprecatedAndNonDeprecatedScanRecordsThenReturnOnlyNonDeprecated() = runTest {
        val deprecatedScanRecord = scanJobRecordNotExecuted.copy(deprecated = true, userProfileId = 999L)
        val nonDeprecatedScanRecord = scanJobRecordNotExecuted.copy(deprecated = false, userProfileId = 1000L)

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(
            listOf(deprecatedScanRecord, nonDeprecatedScanRecord),
        )

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(1, result.size)
        assertEquals(nonDeprecatedScanRecord, result[0])
        assertTrue(result.none { it.deprecated })
    }

    @Test
    fun whenGetAllEligibleScanJobsWithDeprecatedOptOutRemovedRecordThenFilterOut() = runTest {
        val deprecatedOptOutRecord = optOutJobRecordRemoved.copy(deprecated = true)
        val expectedScanRecord = scanJobRecordNotExecuted.copy(userProfileId = 124L)

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(deprecatedOptOutRecord))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 124L)).thenReturn(expectedScanRecord)

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        // Should not return scan record for deprecated opt-out record
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleScanJobsWithNonDeprecatedOptOutRemovedRecordThenInclude() = runTest {
        val nonDeprecatedOptOutRecord = optOutJobRecordRemoved.copy(deprecated = false)
        val expectedScanRecord = scanJobRecordNotExecuted.copy(userProfileId = 124L)

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(nonDeprecatedOptOutRecord))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 124L)).thenReturn(expectedScanRecord)

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertEquals(1, result.size)
        assertEquals(expectedScanRecord, result[0])
    }

    @Test
    fun whenGetAllEligibleScanJobsWithMixOfDeprecatedAndNonDeprecatedOptOutRecordsThenReturnOnlyNonDeprecated() = runTest {
        val deprecatedOptOutRemoved = optOutJobRecordRemoved.copy(
            deprecated = true,
            extractedProfileId = 800L,
            userProfileId = 800L,
        )
        val nonDeprecatedOptOutRemoved = optOutJobRecordRemoved.copy(
            deprecated = false,
            extractedProfileId = 801L,
            userProfileId = 801L,
        )

        val scanForDeprecated = scanJobRecordNotExecuted.copy(userProfileId = 800L)
        val scanForNonDeprecated = scanJobRecordNotExecuted.copy(userProfileId = 801L)

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(
            listOf(deprecatedOptOutRemoved, nonDeprecatedOptOutRemoved),
        )
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 800L)).thenReturn(scanForDeprecated)
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 801L)).thenReturn(scanForNonDeprecated)

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        // Should only return scan record for non-deprecated opt-out
        assertEquals(1, result.size)
        assertEquals(scanForNonDeprecated, result[0])
    }

    @Test
    fun whenGetAllEligibleScanJobsWithDeprecatedOptOutRequestedRecordThenInclude() = runTest {
        // Deprecated flag only affects REMOVED opt-out records for maintenance scans,
        // REQUESTED opt-out records should still trigger confirmation scans
        val deprecatedOptOutRequested = optOutJobRecordRequested.copy(deprecated = true)
        val expectedScanRecord = scanJobRecordNotExecuted

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(deprecatedOptOutRequested))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 123L)).thenReturn(expectedScanRecord)

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        // Should include because deprecated flag only filters REMOVED status for maintenance
        assertEquals(1, result.size)
        assertEquals(expectedScanRecord, result[0])
    }

    @Test
    fun whenGetAllEligibleScanJobsWithAllDeprecatedRecordsThenReturnEmpty() = runTest {
        val deprecatedScanRecord1 = scanJobRecordNotExecuted.copy(deprecated = true, userProfileId = 1001L)
        val deprecatedScanRecord2 = scanJobRecordNoMatch.copy(deprecated = true, userProfileId = 1002L)
        val deprecatedOptOutRemoved = optOutJobRecordRemoved.copy(deprecated = true, userProfileId = 1003L)

        val scanForOptOut = scanJobRecordNotExecuted.copy(userProfileId = 1003L)

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(listOf(deprecatedOptOutRemoved))
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(
            listOf(deprecatedScanRecord1, deprecatedScanRecord2),
        )
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 1003L)).thenReturn(scanForOptOut)

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenGetAllEligibleScanJobsWithComplexMixOfDeprecatedRecordsThenReturnCorrectResults() = runTest {
        // Mix of deprecated and non-deprecated scan records
        val deprecatedScanRecord = scanJobRecordNotExecuted.copy(deprecated = true, userProfileId = 1001L)
        val nonDeprecatedScanRecord = scanJobRecordNoMatch.copy(deprecated = false, userProfileId = 1002L)

        // Mix of deprecated REMOVED and non-deprecated REMOVED opt-out records
        val deprecatedOptOutRemoved = optOutJobRecordRemoved.copy(
            deprecated = true,
            extractedProfileId = 2001L,
            userProfileId = 2001L,
        )
        val nonDeprecatedOptOutRemoved = optOutJobRecordRemoved.copy(
            deprecated = false,
            extractedProfileId = 2002L,
            userProfileId = 2002L,
        )

        // REQUESTED opt-out (deprecated flag shouldn't matter for REQUESTED)
        val deprecatedOptOutRequested = optOutJobRecordRequested.copy(
            deprecated = true,
            extractedProfileId = 3001L,
            userProfileId = 3001L,
        )

        val scanForDeprecatedRemoved = scanJobRecordNotExecuted.copy(userProfileId = 2001L)
        val scanForNonDeprecatedRemoved = scanJobRecordNotExecuted.copy(userProfileId = 2002L)
        val scanForRequested = scanJobRecordNotExecuted.copy(userProfileId = 3001L)

        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(listOf(brokerSchedulingConfig))
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(
            listOf(deprecatedOptOutRemoved, nonDeprecatedOptOutRemoved, deprecatedOptOutRequested),
        )
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(
            listOf(deprecatedScanRecord, nonDeprecatedScanRecord),
        )
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 2001L)).thenReturn(scanForDeprecatedRemoved)
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 2002L)).thenReturn(scanForNonDeprecatedRemoved)
        whenever(mockPirSchedulingRepository.getValidScanJobRecord("test-broker", 3001L)).thenReturn(scanForRequested)

        val result = testee.getAllEligibleScanJobs(currentTimeMillis)

        // Should return:
        // - nonDeprecatedScanRecord (scan record that's not deprecated)
        // - scanForNonDeprecatedRemoved (from non-deprecated REMOVED opt-out)
        // - scanForRequested (from REQUESTED opt-out - deprecated flag doesn't matter)
        // Should NOT return:
        // - deprecatedScanRecord (deprecated scan record)
        // - scanForDeprecatedRemoved (from deprecated REMOVED opt-out)
        assertEquals(3, result.size)
        assertTrue(result.any { it.userProfileId == 1002L }) // nonDeprecatedScanRecord
        assertTrue(result.any { it.userProfileId == 2002L }) // scanForNonDeprecatedRemoved
        assertTrue(result.any { it.userProfileId == 3001L }) // scanForRequested
        assertTrue(result.none { it.userProfileId == 1001L }) // deprecated scan record filtered
        assertTrue(result.none { it.userProfileId == 2001L }) // deprecated removed opt-out filtered
    }
}
