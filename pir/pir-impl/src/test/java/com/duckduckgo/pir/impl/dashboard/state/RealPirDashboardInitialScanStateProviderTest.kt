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
import com.duckduckgo.pir.impl.dashboard.state.PirDashboardInitialScanStateProvider.DashboardBrokerWithStatus.Status
import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.MirrorSite
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealPirDashboardInitialScanStateProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirDashboardInitialScanStateProvider

    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockPirRepository: PirRepository = mock()
    private val mockPirSchedulingRepository: PirSchedulingRepository = mock()

    private val currentTime = 1640995200000L

    @Before
    fun setUp() {
        testee = RealPirDashboardInitialScanStateProvider(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            currentTimeProvider = mockCurrentTimeProvider,
            pirRepository = mockPirRepository,
            pirSchedulingRepository = mockPirSchedulingRepository,
        )

        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(currentTime)
    }

    @Test
    fun whenNoActiveBrokersExistThenGetActiveBrokersAndMirrorSitesTotalReturnsZero() = runTest {
        // Given
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getActiveBrokersAndMirrorSitesTotal()

        // Then
        assertEquals(0, result)
    }

    @Test
    fun whenActiveBrokersExistButNoMirrorSitesThenReturnsActiveBrokersCount() = runTest {
        // Given
        val activeBrokers = listOf("broker1", "broker2", "broker3")
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getActiveBrokersAndMirrorSitesTotal()

        // Then
        assertEquals(3, result)
    }

    @Test
    fun whenActiveBrokersAndExtantMirrorSitesExistThenReturnsTotalCount() = runTest {
        // Given
        val activeBrokers = listOf("broker1", "broker2")
        val mirrorSites = listOf(
            createMirrorSite(
                name = "mirror1",
                parentSite = "broker1",
                addedAt = currentTime - 10000, // Added before current time
                removedAt = 0L, // Not removed
            ),
            createMirrorSite(
                name = "mirror2",
                parentSite = "broker2",
                addedAt = currentTime - 20000, // Added before current time
                removedAt = 0L, // Not removed
            ),
            createMirrorSite(
                name = "mirror3",
                parentSite = "broker3", // Parent not in active brokers
                addedAt = currentTime - 5000,
                removedAt = 0L,
            ),
            createMirrorSite(
                name = "mirror4",
                parentSite = "broker1",
                addedAt = currentTime - 30000,
                removedAt = currentTime - 15000, // Removed before current time
            ),
        )

        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(mirrorSites)

        // When
        val result = testee.getActiveBrokersAndMirrorSitesTotal()

        // Then
        assertEquals(4, result) // 2 active brokers + 2 extant mirror sites with active parents
    }

    @Test
    fun whenNoCompletedBrokersThenGetFullyCompletedBrokersTotalReturnsZero() = runTest {
        // Given
        setupForEmptyBrokersAndJobs()

        // When
        val result = testee.getFullyCompletedBrokersTotal()

        // Then
        assertEquals(0, result)
    }

    @Test
    fun whenSomeCompletedBrokersThenGetFullyCompletedBrokersTotalReturnsCorrectCount() = runTest {
        // Given
        val activeBrokers = listOf(
            createBroker("broker1"),
            createBroker("broker2"),
            createBroker("broker3"),
        )
        val scanJobs = listOf(
            createScanJobRecord("broker1", 1L, ScanJobStatus.MATCHES_FOUND, 1640995200000L),
            createScanJobRecord("broker2", 1L, ScanJobStatus.NO_MATCH_FOUND, 1641081600000L),
            createScanJobRecord("broker3", 1L, ScanJobStatus.NOT_EXECUTED, 0L), // Not completed
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getFullyCompletedBrokersTotal()

        // Then
        assertEquals(2, result) // broker1 and broker2 are completed
    }

    @Test
    fun whenSomeScanNotCompletedForABrokerThenGetFullyCompletedBrokersTotalReturnsCorrectCount() = runTest {
        // Given
        val activeBrokers = listOf(
            createBroker("broker1"),
            createBroker("broker2"),
        )
        val scanJobs = listOf(
            createScanJobRecord("broker1", 1L, ScanJobStatus.MATCHES_FOUND, 1640995200000L),
            createScanJobRecord("broker2", 1L, ScanJobStatus.NO_MATCH_FOUND, 1641081600000L),
            createScanJobRecord("broker2", 1L, ScanJobStatus.NOT_EXECUTED, 0L), // Not completed
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getFullyCompletedBrokersTotal()

        // Then
        assertEquals(1, result) // broker1 is completed
    }

    @Test
    fun whenWithMirrorSitesThenGetFullyCompletedBrokersAndMirrorSitesTotalReturnsCorrectCount() = runTest {
        // Given
        val activeBrokers = listOf(
            createBroker("broker1"),
            createBroker("broker2"),
        )
        val scanJobs = listOf(
            createScanJobRecord("broker1", 1L, ScanJobStatus.MATCHES_FOUND, 1640995200000L),
            createScanJobRecord("broker2", 1L, ScanJobStatus.NO_MATCH_FOUND, 1641081600000L),
            createScanJobRecord("broker2", 1L, ScanJobStatus.NOT_EXECUTED, 0L), // Not completed
        )
        val mirrorSites = listOf(
            createMirrorSite(
                name = "mirror1",
                parentSite = "broker1",
                addedAt = currentTime - 10000, // Added before current time
                removedAt = 0L, // Not removed
            ),
            createMirrorSite(
                name = "mirror2",
                parentSite = "broker2",
                addedAt = currentTime - 20000, // Added before current time
                removedAt = 0L, // Not removed
            ),
            createMirrorSite(
                name = "mirror4",
                parentSite = "broker1",
                addedAt = currentTime - 30000,
                removedAt = currentTime - 15000, // Removed before current time
            ),
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(mirrorSites)

        // When
        val result = testee.getFullyCompletedBrokersTotal()

        // Then
        assertEquals(2, result) // broker1 with it's only mirror site is completed
    }

    @Test
    fun whenNoScannedBrokersThenGetAllScannedBrokersStatusReturnsEmptyList() = runTest {
        // Given
        setupForEmptyBrokersAndJobs()

        // When
        val result = testee.getAllScannedBrokersStatus()

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun whenScannedBrokersExistThenGetAllScannedBrokersStatusReturnsCorrectStatuses() = runTest {
        // Given
        val activeBrokers = listOf(
            createBroker("broker1"),
            createBroker("broker2"),
            createBroker("broker3"),
        )
        val scanJobs = listOf(
            createScanJobRecord("broker1", 1L, ScanJobStatus.MATCHES_FOUND, 1640995200000L),
            createScanJobRecord("broker1", 2L, ScanJobStatus.NO_MATCH_FOUND, 1641081600000L),
            createScanJobRecord("broker2", 1L, ScanJobStatus.MATCHES_FOUND, 1641168000000L),
            createScanJobRecord("broker2", 2L, ScanJobStatus.NOT_EXECUTED, 0L), // In progress
            createScanJobRecord("broker3", 1L, ScanJobStatus.NOT_EXECUTED, 0L), // Never scanned
        )
        val brokerOptOutUrls = mapOf(
            "broker1" to "https://broker1.com/optout",
            "broker2" to "https://broker2.com/optout",
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(brokerOptOutUrls)
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getAllScannedBrokersStatus()

        // Then
        assertEquals(2, result.size) // broker1 and broker2 have been scanned

        val broker1Status = result.find { it.broker.name == "broker1" }!!
        assertEquals(Status.COMPLETED, broker1Status.status)
        assertEquals(1641081600000L, broker1Status.firstScanDateInMillis) // Latest scan date
        assertEquals("https://broker1.com/optout", broker1Status.broker.optOutUrl)

        val broker2Status = result.find { it.broker.name == "broker2" }!!
        assertEquals(Status.IN_PROGRESS, broker2Status.status)
        assertEquals(1641168000000L, broker2Status.firstScanDateInMillis)
        assertEquals("https://broker2.com/optout", broker2Status.broker.optOutUrl)
    }

    @Test
    fun whenBrokersWithMirrorSitesExistThenGetAllScannedBrokersStatusIncludesMirrorSites() = runTest {
        // Given
        val activeBrokers = listOf(createBroker("broker1"))
        val scanJobs = listOf(
            createScanJobRecord("broker1", 1L, ScanJobStatus.MATCHES_FOUND, 1640995200000L),
        )
        val mirrorSites = listOf(
            createMirrorSite(
                name = "mirror1",
                parentSite = "broker1",
                addedAt = currentTime - 10000,
                removedAt = 0L,
            ),
        )

        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidScanJobRecords()).thenReturn(scanJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(mirrorSites)

        // When
        val result = testee.getAllScannedBrokersStatus()

        // Then
        assertEquals(2, result.size) // broker1 + mirror1

        val brokerStatus = result.find { it.broker.name == "broker1" }!!
        assertEquals(Status.COMPLETED, brokerStatus.status)

        val mirrorStatus = result.find { it.broker.name == "mirror1" }!!
        assertEquals(Status.COMPLETED, mirrorStatus.status)
        assertEquals("https://broker1.com", mirrorStatus.broker.parentUrl)
        assertEquals("https://mirror1.com/optout", mirrorStatus.broker.optOutUrl)
    }

    @Test
    fun whenNoExtractedProfilesThenGetScanResultsReturnsEmptyList() = runTest {
        // Given
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getScanResults()

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun whenExtractedProfilesExistThenGetScanResultsReturnsCorrectResults() = runTest {
        // Given
        val extractedProfiles = listOf(
            createExtractedProfile(
                dbId = 1L,
                brokerName = "broker1",
                name = "John Doe",
                addresses = listOf(AddressCityState("New York", "NY")),
                alternativeNames = listOf("Johnny"),
                relatives = listOf("Jane Doe"),
                dateAddedInMillis = 1640995200000L,
            ),
            createExtractedProfile(
                dbId = 2L,
                brokerName = "broker2",
                name = "Jane Smith",
                addresses = listOf(AddressCityState("Los Angeles", "CA")),
                dateAddedInMillis = 1641081600000L,
                deprecated = true, // Should be filtered out
            ),
            createExtractedProfile(
                dbId = 3L,
                brokerName = "broker3", // Inactive broker
                name = "Bob Johnson",
                dateAddedInMillis = 1641168000000L,
            ),
            createExtractedProfile(
                dbId = 1L,
                brokerName = "broker1",
                name = "Hello Doe",
                addresses = listOf(AddressCityState("New York", "NY")),
                alternativeNames = listOf("Hello"),
                relatives = listOf("Hello Doe"),
                dateAddedInMillis = 1640995200000L,
            ).copy(deprecated = true),
        )
        val activeBrokers = listOf(
            createBroker("broker1"),
            createBroker("broker2"),
        )
        val optOutJobs = listOf(
            createOptOutJobRecord(
                extractedProfileId = 1L,
                status = OptOutJobStatus.REQUESTED,
                optOutRequestedDateInMillis = 1641254400000L, // Jan 4, 2022
            ),
        )
        val brokerOptOutUrls = mapOf("broker1" to "https://broker1.com/optout")

        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(extractedProfiles)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(brokerOptOutUrls)
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(optOutJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getScanResults()

        // Then
        assertEquals(1, result.size) // Only profile 1 should be included

        val dashboardResult = result[0]
        assertEquals("John Doe", dashboardResult.extractedProfile.name)
        assertEquals("broker1", dashboardResult.broker.name)
        assertEquals("https://broker1.com", dashboardResult.broker.url)
        assertEquals("https://broker1.com/optout", dashboardResult.broker.optOutUrl)
        assertEquals(1641254400000L, dashboardResult.optOutSubmittedDateInMillis)
        assertEquals(0L, dashboardResult.optOutRemovedDateInMillis) // Should be 0L, not null
        assertEquals(1642464000000L, dashboardResult.estimatedRemovalDateInMillis!!) // Should be calculated
        assertFalse(dashboardResult.hasMatchingRecordOnParentBroker)
    }

    @Test
    fun whenExtractedProfilesWithRemovedOptOutsThenGetScanResultsFiltersCorrectly() = runTest {
        // Given
        val extractedProfiles = listOf(
            createExtractedProfile(dbId = 1L, brokerName = "broker1", name = "John Doe"),
            createExtractedProfile(dbId = 2L, brokerName = "broker1", name = "Jane Smith"),
        )
        val activeBrokers = listOf(createBroker("broker1"))
        val optOutJobs = listOf(
            createOptOutJobRecord(
                extractedProfileId = 1L,
                status = OptOutJobStatus.REQUESTED,
                optOutRequestedDateInMillis = 1641254400000L,
            ),
            createOptOutJobRecord(
                extractedProfileId = 2L,
                status = OptOutJobStatus.REMOVED, // Should be filtered out
                optOutRequestedDateInMillis = 1641254400000L,
                optOutRemovedDateInMillis = 1641254400000L,
            ),
        )

        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(extractedProfiles)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(optOutJobs)
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getScanResults()

        // Then
        assertEquals(2, result.size)
        assertEquals("John Doe", result[0].extractedProfile.name)
        assertEquals("Jane Smith", result[1].extractedProfile.name)
    }

    @Test
    fun whenBrokerHasParentThenGetScanResultsCalculatesMatchingRecordCorrectly() = runTest {
        // Given
        val extractedProfiles = listOf(
            createExtractedProfile(
                dbId = 1L,
                brokerName = "broker1",
                name = "John Doe",
                age = "30",
                alternativeNames = listOf("Johnny"),
                relatives = listOf("Jane Doe", "Jim Doe"),
                addresses = listOf(AddressCityState("New York", "NY"), AddressCityState("Denver", "CO")),
            ),
            createExtractedProfile(
                dbId = 2L,
                brokerName = "broker2", // Child broker
                name = "John Doe",
                age = "30",
                alternativeNames = listOf("Johnny", "John"),
                relatives = listOf("Jane Doe"),
                addresses = listOf(AddressCityState("New York", "NY")),
            ),
            createExtractedProfile(
                dbId = 2L,
                brokerName = "broker3", // Child broker
                name = "Emily Doe",
                age = "30",
                alternativeNames = listOf("Emmy"),
                relatives = listOf("Jane Doe"),
                addresses = listOf(AddressCityState("New York", "NY"), AddressCityState("Denver", "CO")),
            ),
        )
        val activeBrokers = listOf(
            createBroker("broker1"),
            createBroker("broker2", parent = "broker1"), // broker2 is child of broker1
            createBroker("broker3", parent = "broker1"), // broker2 is child of broker1
        )

        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(extractedProfiles)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(emptyList())

        // When
        val result = testee.getScanResults()

        // Then
        assertEquals(3, result.size)

        val parentResult = result.find { it.broker.name == "broker1" }!!
        assertFalse(parentResult.hasMatchingRecordOnParentBroker) // No parent

        val childResult = result.find { it.broker.name == "broker2" }!!
        assertTrue(childResult.hasMatchingRecordOnParentBroker) // Matches parent profile

        val childResult2 = result.find { it.broker.name == "broker3" }!!
        assertTrue(childResult2.hasMatchingRecordOnParentBroker) // Does not matches parent profile
    }

    @Test
    fun whenMirrorSitesExistThenGetScanResultsIncludesMirrorSiteResults() = runTest {
        // Given
        val extractedProfiles = listOf(
            createExtractedProfile(dbId = 1L, brokerName = "broker1", name = "John Doe"),
        )
        val activeBrokers = listOf(createBroker("broker1"))
        val mirrorSites = listOf(
            createMirrorSite(
                name = "mirror1",
                parentSite = "broker1",
                addedAt = currentTime - 10000,
                removedAt = 0L,
            ),
        )

        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(extractedProfiles)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(activeBrokers)
        whenever(mockPirRepository.getAllBrokerOptOutUrls()).thenReturn(emptyMap())
        whenever(mockPirSchedulingRepository.getAllValidOptOutJobRecords()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllMirrorSites()).thenReturn(mirrorSites)

        // When
        val result = testee.getScanResults()

        // Then
        assertEquals(2, result.size) // Original + mirror site

        val originalResult = result.find { it.broker.name == "broker1" }!!
        assertEquals("John Doe", originalResult.extractedProfile.name)

        val mirrorResult = result.find { it.broker.name == "mirror1" }!!
        assertEquals("John Doe", mirrorResult.extractedProfile.name)
        assertEquals("https://broker1.com", mirrorResult.broker.parentUrl)
        assertEquals("https://mirror1.com/optout", mirrorResult.broker.optOutUrl)
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
}
