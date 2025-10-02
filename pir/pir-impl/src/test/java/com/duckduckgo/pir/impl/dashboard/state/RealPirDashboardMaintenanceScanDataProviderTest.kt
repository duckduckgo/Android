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

package com.duckduckgo.pir.impl.dashboard.state

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.MirrorSite
import com.duckduckgo.pir.impl.models.scheduling.BrokerSchedulingConfig
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class RealPirDashboardMaintenanceScanDataProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirDashboardMaintenanceScanDataProvider

    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockPirRepository: PirRepository = mock()
    private val mockPirSchedulingRepository: PirSchedulingRepository = mock()

    private val currentTime = 1640995200000L // Jan 1, 2022

    @Before
    fun setUp() {
        testee = RealPirDashboardMaintenanceScanDataProvider(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            currentTimeProvider = mockCurrentTimeProvider,
            pirRepository = mockPirRepository,
            pirSchedulingRepository = mockPirSchedulingRepository,
        )

        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(currentTime)
    }

    @Test
    fun whenNoExtractedProfilesExistThenGetInProgressOptOutsReturnsEmptyList() = runTest {
        // Given
        setupForEmptyProfiles()

        // When
        val result = testee.getInProgressOptOuts()

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun whenInProgressOptOutsExistThenGetInProgressOptOutsReturnsCorrectResults() = runTest {
        // Given
        val extractedProfiles = listOf(
            createExtractedProfile(dbId = 1L, brokerName = "broker1", name = "John Doe"),
            createExtractedProfile(dbId = 2L, brokerName = "broker1", name = "Jane Smith"),
            createExtractedProfile(dbId = 3L, brokerName = "broker1", name = "Joe Smith"),
        )
        val activeBrokers = listOf(createBroker("broker1"))
        val optOutJobs = listOf(
            createOptOutJobRecord(
                extractedProfileId = 1L,
                status = OptOutJobStatus.REQUESTED,
                optOutRequestedDateInMillis = currentTime - 1000,
                optOutRemovedDateInMillis = 0L, // Not removed
            ),
            createOptOutJobRecord(
                extractedProfileId = 2L,
                status = OptOutJobStatus.REMOVED,
                optOutRequestedDateInMillis = currentTime - 2000,
                optOutRemovedDateInMillis = currentTime - 500, // Removed
            ),
            createOptOutJobRecord(
                extractedProfileId = 3L,
                status = OptOutJobStatus.NOT_EXECUTED,
                optOutRequestedDateInMillis = 0L,
                optOutRemovedDateInMillis = 0L, // Not removed
            ),
        )

        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(extractedProfiles)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(optOutJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getInProgressOptOuts()

        // Then
        assertEquals(2, result.size) // Only profile 1 should be in progress
        assertEquals("John Doe", result[0].extractedProfile.name)
        assertEquals(currentTime - 1000, result[0].optOutSubmittedDateInMillis)
        assertEquals(0L, result[0].optOutRemovedDateInMillis)
        assertEquals("Joe Smith", result[1].extractedProfile.name)
        assertEquals(0L, result[1].optOutSubmittedDateInMillis)
        assertEquals(0L, result[0].optOutRemovedDateInMillis)
    }

    @Test
    fun whenNoRemovedOptOutsExistThenGetRemovedOptOutsReturnsEmptyList() = runTest {
        // Given
        setupForEmptyProfiles()

        // When
        val result = testee.getRemovedOptOuts()

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun whenRemovedOptOutsExistThenGetRemovedOptOutsReturnsCorrectResults() = runTest {
        // Given
        val extractedProfiles = listOf(
            createExtractedProfile(dbId = 1L, brokerName = "broker1", name = "John Doe"),
            createExtractedProfile(dbId = 2L, brokerName = "broker1", name = "Jane Smith"),
            createExtractedProfile(dbId = 3L, brokerName = "broker2", name = "Bob Johnson"),
            createExtractedProfile(dbId = 3L, brokerName = "broker4", name = "Joe Johnson"),
        )
        val activeBrokers = listOf(
            createBroker("broker1"),
            createBroker("broker2"),
        )
        val optOutJobs = listOf(
            createOptOutJobRecord(
                extractedProfileId = 1L,
                status = OptOutJobStatus.REMOVED,
                optOutRequestedDateInMillis = currentTime - 2000,
                optOutRemovedDateInMillis = currentTime - 500,
            ),
            createOptOutJobRecord(
                extractedProfileId = 2L,
                status = OptOutJobStatus.REMOVED,
                optOutRequestedDateInMillis = currentTime - 3000,
                optOutRemovedDateInMillis = currentTime - 1000,
            ),
            createOptOutJobRecord(
                extractedProfileId = 3L,
                status = OptOutJobStatus.REQUESTED,
                optOutRequestedDateInMillis = currentTime - 1000,
                optOutRemovedDateInMillis = 0L, // Not removed
            ),
            createOptOutJobRecord(
                extractedProfileId = 4L,
                status = OptOutJobStatus.NOT_EXECUTED,
                optOutRequestedDateInMillis = 0L,
                optOutRemovedDateInMillis = 0L, // Not removed
            ),
        )
        val brokerOptOutUrls = mapOf(
            "broker1" to "https://broker1.com/optout",
            "broker2" to "https://broker2.com/optout",
        )

        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(extractedProfiles)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(brokerOptOutUrls)
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(optOutJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getRemovedOptOuts()

        // Then
        assertEquals(2, result.size) // Only profiles 1 and 2 should be removed

        val johnDoeResult = result.find { it.result.extractedProfile.name == "John Doe" }!!
        assertEquals(currentTime - 500, johnDoeResult.result.optOutRemovedDateInMillis)
        // Both John and Jane are from broker1, so matches should be 2 each (grouped by broker)
        assertEquals(2, johnDoeResult.matches)

        val janeSmithResult = result.find { it.result.extractedProfile.name == "Jane Smith" }!!
        assertEquals(currentTime - 1000, janeSmithResult.result.optOutRemovedDateInMillis)
        // Both John and Jane are from broker1, so matches should be 2 each (grouped by broker)
        assertEquals(2, janeSmithResult.matches)
    }

    @Test
    fun whenNoValidScanJobsExistThenGetLastScanDetailsReturnsEmptyDetails() = runTest {
        // Given
        setupForEmptyBrokersAndJobs()

        // When
        val result = testee.getLastScanDetails()

        // Then
        assertEquals(0L, result.dateInMillis)
        assertEquals(0, result.brokerMatches.size)
    }

    @Test
    fun whenRecentScanJobsExistThenGetLastScanDetailsReturnsCorrectResults() = runTest {
        // Given
        val activeBrokers = listOf(
            createBroker("broker1"),
            createBroker("broker2"),
        )
        val scanJobs = listOf(
            createScanJobRecord(
                "broker1",
                1L,
                ScanJobStatus.MATCHES_FOUND,
                currentTime - TimeUnit.DAYS.toMillis(2), // 2 days ago
            ),
            createScanJobRecord(
                "broker2",
                1L,
                ScanJobStatus.NO_MATCH_FOUND,
                currentTime - TimeUnit.DAYS.toMillis(5), // 5 days ago
            ),
            createScanJobRecord(
                "broker1",
                2L,
                ScanJobStatus.MATCHES_FOUND,
                currentTime - TimeUnit.DAYS.toMillis(10), // 10 days ago - outside range
            ),
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getLastScanDetails()

        // Then
        assertEquals(2, result.brokerMatches.size) // Only scans within 8 days
        assertEquals(
            currentTime - TimeUnit.DAYS.toMillis(5),
            result.dateInMillis,
        ) // Earliest scan within range

        val broker1Match = result.brokerMatches.find { it.broker.name == "broker1" }!!
        assertEquals(currentTime - TimeUnit.DAYS.toMillis(2), broker1Match.dateInMillis)

        val broker2Match = result.brokerMatches.find { it.broker.name == "broker2" }!!
        assertEquals(currentTime - TimeUnit.DAYS.toMillis(5), broker2Match.dateInMillis)
    }

    @Test
    fun whenNoScheduledScanJobsExistThenGetNextScanDetailsReturnsEmptyDetails() = runTest {
        // Given
        setupForEmptyBrokersAndJobs()
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())

        // When
        val result = testee.getNextScanDetails()

        // Then
        assertEquals(0L, result.dateInMillis)
        assertEquals(0, result.brokerMatches.size)
    }

    @Test
    fun whenScheduledScanJobsExistThenGetNextScanDetailsReturnsCorrectResults() = runTest {
        // Given
        val activeBrokers = listOf(
            createBroker("broker1"),
            createBroker("broker2"),
        )
        val schedulingConfigs = listOf(
            createBrokerSchedulingConfig(
                "broker1",
                maintenanceScanInMillis = TimeUnit.DAYS.toMillis(7),
            ),
            createBrokerSchedulingConfig(
                "broker2",
                maintenanceScanInMillis = TimeUnit.DAYS.toMillis(14),
            ),
        )
        val scanJobs = listOf(
            createScanJobRecord(
                "broker1",
                1L,
                ScanJobStatus.MATCHES_FOUND,
                currentTime - TimeUnit.DAYS.toMillis(3), // Last scan 3 days ago
            ),
            createScanJobRecord(
                "broker2",
                1L,
                ScanJobStatus.NO_MATCH_FOUND,
                currentTime - TimeUnit.DAYS.toMillis(1), // Last scan 1 day ago
            ),
        )

        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(schedulingConfigs)
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getNextScanDetails()

        // Then
        assertEquals(1, result.brokerMatches.size) // Only broker1 scheduled within 8 days

        val broker1Match = result.brokerMatches[0]
        assertEquals("broker1", broker1Match.broker.name)
        // Next scan = last scan + maintenance interval = (currentTime - 3 days) + 7 days = currentTime + 4 days
        val expectedNextScan = currentTime - TimeUnit.DAYS.toMillis(3) + TimeUnit.DAYS.toMillis(7)
        assertEquals(expectedNextScan, broker1Match.dateInMillis)
        assertEquals(expectedNextScan, result.dateInMillis)
    }

    @Test
    fun whenNotExecutedScanJobsExistThenGetNextScanDetailsSchedulesImmediately() = runTest {
        // Given
        val activeBrokers = listOf(createBroker("broker1"))
        val schedulingConfigs = listOf(
            createBrokerSchedulingConfig(
                "broker1",
                maintenanceScanInMillis = TimeUnit.DAYS.toMillis(7),
            ),
        )
        val scanJobs = listOf(
            createScanJobRecord("broker1", 1L, ScanJobStatus.NOT_EXECUTED, 0L),
        )

        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(schedulingConfigs)
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getNextScanDetails()

        // Then
        assertEquals(1, result.brokerMatches.size)
        assertEquals(currentTime, result.brokerMatches[0].dateInMillis) // Should be immediate
        assertEquals(currentTime, result.dateInMillis)
    }

    @Test
    fun whenErrorScanJobsExistThenGetNextScanDetailsUsesRetryErrorInterval() = runTest {
        // Given
        val activeBrokers = listOf(createBroker("broker1"))
        val schedulingConfigs = listOf(
            createBrokerSchedulingConfig(
                "broker1",
                retryErrorInMillis = TimeUnit.HOURS.toMillis(2),
                maintenanceScanInMillis = TimeUnit.DAYS.toMillis(7),
            ),
        )
        val scanJobs = listOf(
            createScanJobRecord(
                "broker1",
                1L,
                ScanJobStatus.ERROR,
                currentTime - TimeUnit.HOURS.toMillis(1), // Error 1 hour ago
            ),
        )

        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(schedulingConfigs)
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getNextScanDetails()

        // Then
        assertEquals(1, result.brokerMatches.size)
        val expectedNextScan = currentTime - TimeUnit.HOURS.toMillis(1) + TimeUnit.HOURS.toMillis(2)
        assertEquals(expectedNextScan, result.brokerMatches[0].dateInMillis)
    }

    @Test
    fun whenOptOutJobIsRequestedButOutOfRangeThenGetNextScanDetailsReturnsMaintenanceFromScanJob() =
        runTest {
            // Given
            val activeBrokers = listOf(
                createBroker("broker1"),
            )
            val schedulingConfigs = listOf(
                createBrokerSchedulingConfig(
                    "broker1",
                    maintenanceScanInMillis = TimeUnit.DAYS.toMillis(10),
                    confirmOptOutScanInMillis = TimeUnit.DAYS.toMillis(10),
                ),
            )
            val scanJobs = listOf(
                createScanJobRecord(
                    // Next scan is in 7 days, in range
                    "broker1",
                    1L,
                    ScanJobStatus.MATCHES_FOUND,
                    currentTime - TimeUnit.DAYS.toMillis(3), // Last scan 3 days ago
                ),
            )

            val optOutJobs = listOf(
                createOptOutJobRecord(
                    // Next scan is in 10 days, out of range
                    brokerName = "broker1",
                    extractedProfileId = 1L,
                    status = OptOutJobStatus.REQUESTED,
                    optOutRequestedDateInMillis = currentTime, // Requested today
                ),
                // Next scan is in 10 days, out of rang
                createOptOutJobRecord(
                    brokerName = "broker1",
                    extractedProfileId = 2L,
                    status = OptOutJobStatus.REMOVED,
                    optOutRequestedDateInMillis = currentTime - TimeUnit.DAYS.toMillis(5), // Requested 5 days ago
                    optOutRemovedDateInMillis = currentTime, // Removed today
                ),
            )

            whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(
                optOutJobs,
            )
            whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
            whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
            whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(schedulingConfigs)
            whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
            whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

            // When
            val result = testee.getNextScanDetails()

            // Then
            assertEquals(1, result.brokerMatches.size) // Only broker1 scheduled within 8 days

            val broker1Match = result.brokerMatches[0]
            assertEquals("broker1", broker1Match.broker.name)
            // Next scan = last scan + maintenance interval = (currentTime - 3 days) + 10 days = currentTime + 7 days
            val expectedNextScan =
                currentTime - TimeUnit.DAYS.toMillis(3) + TimeUnit.DAYS.toMillis(10)
            assertEquals(expectedNextScan, broker1Match.dateInMillis)
            assertEquals(expectedNextScan, result.dateInMillis)
        }

    @Test
    fun whenOptOutJobIsRequestedAndIsNextScanThenGetNextScanDetailsReturnsConfirmationFromOptOut() =
        runTest {
            // Given
            val activeBrokers = listOf(
                createBroker("broker1"),
            )
            val schedulingConfigs = listOf(
                createBrokerSchedulingConfig(
                    "broker1",
                    maintenanceScanInMillis = TimeUnit.DAYS.toMillis(10),
                    confirmOptOutScanInMillis = TimeUnit.DAYS.toMillis(5),
                ),
            )
            val scanJobs = listOf(
                // Next scan is in 7 days, in range
                createScanJobRecord(
                    "broker1",
                    1L,
                    ScanJobStatus.MATCHES_FOUND,
                    currentTime - TimeUnit.DAYS.toMillis(3), // Last scan 3 days ago
                ),
            )

            val optOutJobs = listOf(
                createOptOutJobRecord(
                    // Next scan is in 4 days, in range
                    brokerName = "broker1",
                    extractedProfileId = 1L,
                    status = OptOutJobStatus.REQUESTED,
                    optOutRequestedDateInMillis = currentTime - TimeUnit.DAYS.toMillis(1), // Requested yesterday
                ),
                createOptOutJobRecord(
                    // Next scan was yesterday (today - 1), out of range
                    brokerName = "broker1",
                    extractedProfileId = 2L,
                    status = OptOutJobStatus.REQUESTED,
                    optOutRequestedDateInMillis = currentTime - TimeUnit.DAYS.toMillis(6), // Requested 6 days ago
                ),
            )

            whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(
                optOutJobs,
            )
            whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
            whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
            whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(schedulingConfigs)
            whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
            whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

            // When
            val result = testee.getNextScanDetails()

            // Then
            assertEquals(1, result.brokerMatches.size) // Only broker1 scheduled within 8 days

            val broker1Match = result.brokerMatches[0]
            assertEquals("broker1", broker1Match.broker.name)
            // Next scan is from the opt-out job = requested date + confirm interval = (currentTime - 1 day) + 5 days = currentTime + 4 days
            val expectedNextScan =
                currentTime - TimeUnit.DAYS.toMillis(1) + TimeUnit.DAYS.toMillis(5)
            assertEquals(expectedNextScan, broker1Match.dateInMillis)
            assertEquals(expectedNextScan, result.dateInMillis)
        }

    @Test
    fun whenOptOutJobIsRemovedAndIsNextScanThenGetNextScanDetailsReturnsMaintenanceFromOptOut() =
        runTest {
            // Given
            val activeBrokers = listOf(
                createBroker("broker1"),
            )
            val schedulingConfigs = listOf(
                createBrokerSchedulingConfig(
                    "broker1",
                    maintenanceScanInMillis = TimeUnit.DAYS.toMillis(10),
                    confirmOptOutScanInMillis = TimeUnit.DAYS.toMillis(5),
                ),
            )
            val scanJobs = listOf(
                createScanJobRecord(
                    // Next Scan is in 7 days, in range
                    "broker1",
                    1L,
                    ScanJobStatus.MATCHES_FOUND,
                    currentTime - TimeUnit.DAYS.toMillis(3), // Last scan 3 days ago
                ),
            )

            val optOutJobs = listOf(
                createOptOutJobRecord(
                    // Scan is yesterday, out of range
                    brokerName = "broker1",
                    extractedProfileId = 1L,
                    status = OptOutJobStatus.REMOVED,
                    optOutRequestedDateInMillis = currentTime - TimeUnit.DAYS.toMillis(15), // Requested 15 days ago
                    optOutRemovedDateInMillis = currentTime - TimeUnit.DAYS.toMillis(11), // Removed 11 days ago
                ),
                createOptOutJobRecord(
                    // Next scan is in 5 days, in range
                    brokerName = "broker1",
                    extractedProfileId = 2L,
                    status = OptOutJobStatus.REMOVED,
                    optOutRequestedDateInMillis = currentTime - TimeUnit.DAYS.toMillis(15), // Requested 15 days ago
                    optOutRemovedDateInMillis = currentTime - TimeUnit.DAYS.toMillis(5), // Removed 5 days ago
                ),
            )

            whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(
                optOutJobs,
            )
            whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
            whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
            whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(schedulingConfigs)
            whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
            whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

            // When
            val result = testee.getNextScanDetails()

            // Then
            assertEquals(1, result.brokerMatches.size) // Only broker1 scheduled within 8 days

            val broker1Match = result.brokerMatches[0]
            assertEquals("broker1", broker1Match.broker.name)
            // Next scan is from removed opt out's maintenance scan
            val expectedNextScan =
                currentTime - TimeUnit.DAYS.toMillis(5) + TimeUnit.DAYS.toMillis(10)
            assertEquals(expectedNextScan, broker1Match.dateInMillis)
            assertEquals(expectedNextScan, result.dateInMillis)
        }

    @Test
    fun whenAllAreInRangeThenGetNextScanDetailsReturnsMaintenanceFromScan() = runTest {
        // Given
        val activeBrokers = listOf(
            createBroker("broker1"),
        )
        val schedulingConfigs = listOf(
            createBrokerSchedulingConfig(
                "broker1",
                maintenanceScanInMillis = TimeUnit.DAYS.toMillis(10),
                confirmOptOutScanInMillis = TimeUnit.DAYS.toMillis(5),
            ),
        )
        val scanJobs = listOf(
            createScanJobRecord(
                // Next scan is in 1 day, in range
                "broker1",
                1L,
                ScanJobStatus.MATCHES_FOUND,
                currentTime - TimeUnit.DAYS.toMillis(9), // Last scan 9 days ago
            ),
        )

        val optOutJobs = listOf(
            createOptOutJobRecord(
                // Next scan is in 7 days, in range
                brokerName = "broker1",
                extractedProfileId = 1L,
                status = OptOutJobStatus.REMOVED,
                optOutRequestedDateInMillis = currentTime - TimeUnit.DAYS.toMillis(15), // Requested 15 days ago
                optOutRemovedDateInMillis = currentTime - TimeUnit.DAYS.toMillis(3), // Removed 3 days ago
            ),
            createOptOutJobRecord(
                // Next Scan is in 2 days, in range
                brokerName = "broker1",
                extractedProfileId = 2L,
                status = OptOutJobStatus.REQUESTED,
                optOutRequestedDateInMillis = currentTime - TimeUnit.DAYS.toMillis(3), // Requested 3 days ago
            ),
        )

        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(optOutJobs)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(schedulingConfigs)
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getNextScanDetails()

        // Then
        assertEquals(1, result.brokerMatches.size) // Only broker1 scheduled within 8 days

        val broker1Match = result.brokerMatches[0]
        assertEquals("broker1", broker1Match.broker.name)
        // Next scan is from scan's maintenance scan
        val expectedNextScan = currentTime - TimeUnit.DAYS.toMillis(9) + TimeUnit.DAYS.toMillis(10)
        assertEquals(expectedNextScan, broker1Match.dateInMillis)
        assertEquals(expectedNextScan, result.dateInMillis)
    }

    @Test
    fun whenNoneInRangeThenGetNextScanDetailsReturnsEmpty() = runTest {
        // Given
        val activeBrokers = listOf(
            createBroker("broker1"),
        )
        val schedulingConfigs = listOf(
            createBrokerSchedulingConfig(
                "broker1",
                maintenanceScanInMillis = TimeUnit.DAYS.toMillis(10),
                confirmOptOutScanInMillis = TimeUnit.DAYS.toMillis(5),
            ),
        )
        val scanJobs = listOf(
            // Next scan is in 9 days, out of range
            createScanJobRecord(
                "broker1",
                1L,
                ScanJobStatus.MATCHES_FOUND,
                currentTime - TimeUnit.DAYS.toMillis(1), // Last scan 1 days ago
            ),
        )

        val optOutJobs = listOf(
            createOptOutJobRecord(
                // Next scan was a day ago, out of range
                brokerName = "broker1",
                extractedProfileId = 1L,
                status = OptOutJobStatus.REQUESTED,
                optOutRequestedDateInMillis = currentTime - TimeUnit.DAYS.toMillis(6), // Requested yesterday
            ),
        )

        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(optOutJobs)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirRepository.getAllBrokerSchedulingConfigs()).thenReturn(schedulingConfigs)
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getNextScanDetails()

        // Then
        assertEquals(0, result.brokerMatches.size)
    }

    @Test
    fun whenNoScannedBrokersExistThenGetScannedBrokerCountReturnsZero() = runTest {
        // Given
        setupForEmptyBrokersAndJobs()

        // When
        val result = testee.getScannedBrokerCount()

        // Then
        assertEquals(0, result)
    }

    @Test
    fun whenScannedBrokersExistThenGetScannedBrokerCountReturnsCorrectCount() = runTest {
        // Given
        val activeBrokers = listOf(
            createBroker("broker1"),
            createBroker("broker2"),
            createBroker("broker3"),
        )
        val scanJobs = listOf(
            createScanJobRecord("broker1", 1L, ScanJobStatus.MATCHES_FOUND, currentTime - 1000),
            createScanJobRecord("broker2", 1L, ScanJobStatus.NO_MATCH_FOUND, currentTime - 2000),
            createScanJobRecord("broker3", 1L, ScanJobStatus.NOT_EXECUTED, 0L), // Never scanned
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getScannedBrokerCount()

        // Then
        assertEquals(2, result) // Only broker1 and broker2 have been scanned
    }

    @Test
    fun whenMirrorSitesExistThenGetLastScanDetailsIncludesMirrorSites() = runTest {
        // Given
        val activeBrokers = listOf(createBroker("broker1"))
        val scanJobs = listOf(
            createScanJobRecord(
                "broker1",
                1L,
                ScanJobStatus.MATCHES_FOUND,
                currentTime - TimeUnit.DAYS.toMillis(2),
            ),
        )
        val mirrorSites = listOf(
            createMirrorSite(
                name = "mirror1",
                parentSite = "broker1",
                addedAt = currentTime - TimeUnit.DAYS.toMillis(5), // Added before scan
                removedAt = 0L,
            ),
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(mirrorSites)

        // When
        val result = testee.getLastScanDetails()

        // Then
        assertEquals(2, result.brokerMatches.size) // broker1 + mirror1

        val brokerMatch = result.brokerMatches.find { it.broker.name == "broker1" }!!
        assertEquals(currentTime - TimeUnit.DAYS.toMillis(2), brokerMatch.dateInMillis)

        val mirrorMatch = result.brokerMatches.find { it.broker.name == "mirror1" }!!
        assertEquals(
            currentTime - TimeUnit.DAYS.toMillis(2),
            mirrorMatch.dateInMillis,
        ) // Same as parent
        assertEquals("https://broker1.com", mirrorMatch.broker.parentUrl)
    }

    @Test
    fun whenMirrorSiteAddedAfterScanThenNotIncludedInLastScanDetails() = runTest {
        // Given
        val activeBrokers = listOf(createBroker("broker1"))
        val scanJobs = listOf(
            createScanJobRecord(
                "broker1",
                1L,
                ScanJobStatus.MATCHES_FOUND,
                currentTime - TimeUnit.DAYS.toMillis(2),
            ),
        )
        val mirrorSites = listOf(
            createMirrorSite(
                name = "mirror1",
                parentSite = "broker1",
                addedAt = currentTime - TimeUnit.DAYS.toMillis(1), // Added after scan
                removedAt = 0L,
            ),
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(mirrorSites)

        // When
        val result = testee.getLastScanDetails()

        // Then
        assertEquals(1, result.brokerMatches.size) // Only broker1, mirror1 not extant at scan time
        assertEquals("broker1", result.brokerMatches[0].broker.name)
    }

    private suspend fun setupForEmptyProfiles() {
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())
    }

    private suspend fun setupForEmptyBrokersAndJobs() {
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())
    }

    private fun createBroker(
        name: String,
        url: String = "https://$name.com",
        parent: String? = null,
    ): Broker {
        return Broker(
            name = name,
            fileName = "$name.json",
            url = url,
            version = "1.0",
            parent = parent,
            addedDatetime = currentTime - 100000,
            removedAt = 0L,
        )
    }

    private fun createScanJobRecord(
        brokerName: String,
        userProfileId: Long,
        status: ScanJobStatus,
        lastScanDateInMillis: Long,
    ): ScanJobRecord {
        return ScanJobRecord(
            brokerName = brokerName,
            userProfileId = userProfileId,
            status = status,
            lastScanDateInMillis = lastScanDateInMillis,
        )
    }

    private fun createOptOutJobRecord(
        extractedProfileId: Long,
        brokerName: String = "broker1",
        userProfileId: Long = 1L,
        status: OptOutJobStatus = OptOutJobStatus.REQUESTED,
        optOutRequestedDateInMillis: Long = 0L,
        optOutRemovedDateInMillis: Long = 0L,
    ): OptOutJobRecord {
        return OptOutJobRecord(
            extractedProfileId = extractedProfileId,
            brokerName = brokerName,
            userProfileId = userProfileId,
            status = status,
            attemptCount = 0,
            lastOptOutAttemptDateInMillis = 0L,
            optOutRequestedDateInMillis = optOutRequestedDateInMillis,
            optOutRemovedDateInMillis = optOutRemovedDateInMillis,
        )
    }

    private fun createExtractedProfile(
        dbId: Long,
        brokerName: String,
        name: String,
        age: String = "30",
        addresses: List<AddressCityState> = emptyList(),
        alternativeNames: List<String> = emptyList(),
        relatives: List<String> = emptyList(),
        dateAddedInMillis: Long = currentTime,
        deprecated: Boolean = false,
    ): ExtractedProfile {
        return ExtractedProfile(
            dbId = dbId,
            profileQueryId = 1L,
            brokerName = brokerName,
            name = name,
            alternativeNames = alternativeNames,
            age = age,
            addresses = addresses,
            relatives = relatives,
            dateAddedInMillis = dateAddedInMillis,
            deprecated = deprecated,
        )
    }

    private fun createMirrorSite(
        name: String,
        parentSite: String,
        url: String = "https://$name.com",
        optOutUrl: String = "https://$name.com/optout",
        addedAt: Long,
        removedAt: Long,
    ): MirrorSite {
        return MirrorSite(
            name = name,
            parentSite = parentSite,
            url = url,
            optOutUrl = optOutUrl,
            addedAt = addedAt,
            removedAt = removedAt,
        )
    }

    private fun createBrokerSchedulingConfig(
        brokerName: String,
        retryErrorInMillis: Long = TimeUnit.HOURS.toMillis(4),
        confirmOptOutScanInMillis: Long = TimeUnit.HOURS.toMillis(72),
        maintenanceScanInMillis: Long = TimeUnit.DAYS.toMillis(30),
        maxAttempts: Int = 3,
    ): BrokerSchedulingConfig {
        return BrokerSchedulingConfig(
            brokerName = brokerName,
            retryErrorInMillis = retryErrorInMillis,
            confirmOptOutScanInMillis = confirmOptOutScanInMillis,
            maintenanceScanInMillis = maintenanceScanInMillis,
            maxAttempts = maxAttempts,
        )
    }
}
