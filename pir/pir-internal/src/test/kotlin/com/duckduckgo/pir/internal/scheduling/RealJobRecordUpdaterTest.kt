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

package com.duckduckgo.pir.internal.scheduling

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.internal.models.ExtractedProfile
import com.duckduckgo.pir.internal.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.internal.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.internal.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.PirSchedulingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealJobRecordUpdaterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var toTest: RealJobRecordUpdater

    private val mockSchedulingRepository: PirSchedulingRepository = mock()
    private val mockRepository: PirRepository = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()

    @Before
    fun setUp() {
        toTest = RealJobRecordUpdater(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            currentTimeProvider = mockCurrentTimeProvider,
            schedulingRepository = mockSchedulingRepository,
            repository = mockRepository,
        )

        // Set up default behavior for current time
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(TEST_CURRENT_TIME)
    }

    // Test data
    private val testBrokerName = "test-broker"
    private val testProfileQueryId = 123L
    private val testExtractedProfileId = 456L
    private val testAttemptCount = 2

    private val testOptOutJobRecord = OptOutJobRecord(
        brokerName = testBrokerName,
        userProfileId = testProfileQueryId,
        extractedProfileId = testExtractedProfileId,
        status = OptOutJobStatus.NOT_EXECUTED,
        attemptCount = testAttemptCount,
        lastOptOutAttemptDateInMillis = 1000L,
        optOutRequestedDateInMillis = 2000L,
        optOutRemovedDateInMillis = 0L,
    )

    private val testExtractedProfile1 = ExtractedProfile(
        dbId = 100L,
        profileQueryId = testProfileQueryId,
        brokerName = testBrokerName,
        name = "John Doe",
        profileUrl = "https://example.com/profile/100",
        identifier = "id100",
    )

    private val testExtractedProfile2 = ExtractedProfile(
        dbId = 200L,
        profileQueryId = testProfileQueryId,
        brokerName = testBrokerName,
        name = "Jane Smith",
        profileUrl = "https://example.com/profile/200",
        identifier = "id200",
    )

    private val testExtractedProfile3 = ExtractedProfile(
        dbId = 300L,
        profileQueryId = testProfileQueryId,
        brokerName = testBrokerName,
        name = "Bob Johnson",
        profileUrl = "https://example.com/profile/300",
        identifier = "id300",
    )

    @Test
    fun whenUpdateScanMatchesFoundThenUpdatesJobRecordWithCorrectStatus() = runTest {
        toTest.updateScanMatchesFound(testBrokerName, testProfileQueryId)

        verify(mockSchedulingRepository).updateScanJobRecordStatus(
            newStatus = ScanJobStatus.MATCHES_FOUND,
            newLastScanDateMillis = TEST_CURRENT_TIME,
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
        )
    }

    @Test
    fun whenUpdateScanNoMatchFoundThenUpdatesJobRecordWithCorrectStatus() = runTest {
        toTest.updateScanNoMatchFound(testBrokerName, testProfileQueryId)

        verify(mockSchedulingRepository).updateScanJobRecordStatus(
            newStatus = ScanJobStatus.NO_MATCH_FOUND,
            newLastScanDateMillis = TEST_CURRENT_TIME,
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
        )
    }

    @Test
    fun whenUpdateScanErrorThenUpdatesJobRecordWithCorrectStatus() = runTest {
        toTest.updateScanError(testBrokerName, testProfileQueryId)

        verify(mockSchedulingRepository).updateScanJobRecordStatus(
            newStatus = ScanJobStatus.ERROR,
            newLastScanDateMillis = TEST_CURRENT_TIME,
            brokerName = testBrokerName,
            profileQueryId = testProfileQueryId,
        )
    }

    @Test
    fun whenMarkOptOutAsAttemptedAndJobRecordExistsThenIncrementsAttemptCountAndUpdatesTimestamp() = runTest {
        whenever(mockSchedulingRepository.getValidOptOutJobRecord(testExtractedProfileId))
            .thenReturn(testOptOutJobRecord)

        toTest.markOptOutAsAttempted(testExtractedProfileId)

        verify(mockSchedulingRepository).saveOptOutJobRecord(
            testOptOutJobRecord.copy(
                attemptCount = testAttemptCount + 1,
                lastOptOutAttemptDateInMillis = TEST_CURRENT_TIME,
            ),
        )
    }

    @Test
    fun whenMarkOptOutAsAttemptedAndJobRecordDoesNotExistThenDoesNothing() = runTest {
        whenever(mockSchedulingRepository.getValidOptOutJobRecord(testExtractedProfileId))
            .thenReturn(null)

        toTest.markOptOutAsAttempted(testExtractedProfileId)

        verify(mockSchedulingRepository).getValidOptOutJobRecord(testExtractedProfileId)
        verifyNoInteractions(mockCurrentTimeProvider)
    }

    @Test
    fun whenUpdateOptOutRequestedAndJobRecordExistsThenUpdatesStatusAndTimestamp() = runTest {
        whenever(mockSchedulingRepository.getValidOptOutJobRecord(testExtractedProfileId))
            .thenReturn(testOptOutJobRecord)

        toTest.updateOptOutRequested(testExtractedProfileId)

        verify(mockSchedulingRepository).saveOptOutJobRecord(
            testOptOutJobRecord.copy(
                status = OptOutJobStatus.REQUESTED,
                optOutRequestedDateInMillis = TEST_CURRENT_TIME,
            ),
        )
    }

    @Test
    fun whenUpdateOptOutRequestedAndJobRecordDoesNotExistThenDoesNothing() = runTest {
        whenever(mockSchedulingRepository.getValidOptOutJobRecord(testExtractedProfileId))
            .thenReturn(null)

        toTest.updateOptOutRequested(testExtractedProfileId)

        verify(mockSchedulingRepository).getValidOptOutJobRecord(testExtractedProfileId)
        verifyNoInteractions(mockCurrentTimeProvider)
    }

    @Test
    fun whenUpdateOptOutErrorAndJobRecordExistsThenUpdatesStatusToError() = runTest {
        whenever(mockSchedulingRepository.getValidOptOutJobRecord(testExtractedProfileId))
            .thenReturn(testOptOutJobRecord)

        toTest.updateOptOutError(testExtractedProfileId)

        verify(mockSchedulingRepository).saveOptOutJobRecord(
            testOptOutJobRecord.copy(
                status = OptOutJobStatus.ERROR,
            ),
        )
    }

    @Test
    fun whenUpdateOptOutErrorAndJobRecordDoesNotExistThenDoesNothing() = runTest {
        whenever(mockSchedulingRepository.getValidOptOutJobRecord(testExtractedProfileId))
            .thenReturn(null)

        toTest.updateOptOutError(testExtractedProfileId)

        verify(mockSchedulingRepository).getValidOptOutJobRecord(testExtractedProfileId)
        verifyNoInteractions(mockCurrentTimeProvider)
    }

    @Test
    fun whenMarkRemovedProfilesWithSomeRemovedProfilesThenMarksThemAsRemovedInCorrectOrder() = runTest {
        // Setup: stored profiles [profile1, profile2, profile3] but new profiles only [profile1, profile3]
        // Result: profile2 should be marked as removed
        val storedProfiles = listOf(testExtractedProfile1, testExtractedProfile2, testExtractedProfile3)

        // New profiles come from the script so dbId is not set
        val newProfiles = listOf(
            testExtractedProfile1.copy(dbId = 0L),
            testExtractedProfile3.copy(dbId = 0L),
        ) // profile2 is missing

        val optOutJobRecord2 = testOptOutJobRecord.copy(extractedProfileId = 200L)

        whenever(mockRepository.getExtractedProfiles(testBrokerName, testProfileQueryId))
            .thenReturn(storedProfiles)
        whenever(mockSchedulingRepository.getValidOptOutJobRecord(200L))
            .thenReturn(optOutJobRecord2)

        toTest.markRemovedOptOutJobRecords(newProfiles, testBrokerName, testProfileQueryId)

        // Verify the order of operations:
        // 1. Get current time first
        // 2. Get stored profiles from repository
        // 3. Update the removed profile's job record
        val inOrder = inOrder(mockCurrentTimeProvider, mockRepository, mockSchedulingRepository)
        inOrder.verify(mockCurrentTimeProvider).currentTimeMillis()
        inOrder.verify(mockRepository).getExtractedProfiles(testBrokerName, testProfileQueryId)
        inOrder.verify(mockSchedulingRepository).getValidOptOutJobRecord(200L)
        inOrder.verify(mockSchedulingRepository).saveOptOutJobRecord(
            optOutJobRecord2.copy(
                status = OptOutJobStatus.REMOVED,
                optOutRemovedDateInMillis = TEST_CURRENT_TIME,
            ),
        )
    }

    @Test
    fun whenMarkRemovedProfilesWithNoRemovedProfilesThenDoesNotUpdateAnyRecords() = runTest {
        // Setup: stored profiles [profile1, profile2] and new profiles [profile1, profile2]
        // Result: no profiles should be marked as removed
        val storedProfiles = listOf(testExtractedProfile1, testExtractedProfile2)

        // New profiles come from the script so dbId is not set
        val newProfiles = listOf(
            testExtractedProfile1.copy(dbId = 0L),
            testExtractedProfile2.copy(dbId = 0L),
        )

        whenever(mockRepository.getExtractedProfiles(testBrokerName, testProfileQueryId))
            .thenReturn(storedProfiles)

        toTest.markRemovedOptOutJobRecords(newProfiles, testBrokerName, testProfileQueryId)

        verify(mockRepository).getExtractedProfiles(testBrokerName, testProfileQueryId)
        // Should not call getValidOptOutJobRecord or saveOptOutJobRecord since no profiles were removed
        verify(mockSchedulingRepository, never()).getValidOptOutJobRecord(any())
        verify(mockSchedulingRepository, never()).saveOptOutJobRecord(any())
    }

    @Test
    fun whenMarkRemovedProfilesWithAllProfilesRemovedThenMarksAllAsRemoved() = runTest {
        // Setup: stored profiles [profile1, profile2] but new profiles is empty
        // Result: both profiles should be marked as removed
        val storedProfiles = listOf(testExtractedProfile1, testExtractedProfile2)
        val newProfiles = emptyList<ExtractedProfile>()

        val optOutJobRecord1 = testOptOutJobRecord.copy(extractedProfileId = 100L)
        val optOutJobRecord2 = testOptOutJobRecord.copy(extractedProfileId = 200L)

        whenever(mockRepository.getExtractedProfiles(testBrokerName, testProfileQueryId))
            .thenReturn(storedProfiles)
        whenever(mockSchedulingRepository.getValidOptOutJobRecord(100L))
            .thenReturn(optOutJobRecord1)
        whenever(mockSchedulingRepository.getValidOptOutJobRecord(200L))
            .thenReturn(optOutJobRecord2)

        toTest.markRemovedOptOutJobRecords(newProfiles, testBrokerName, testProfileQueryId)

        verify(mockSchedulingRepository).saveOptOutJobRecord(
            optOutJobRecord1.copy(
                status = OptOutJobStatus.REMOVED,
                optOutRemovedDateInMillis = TEST_CURRENT_TIME,
            ),
        )
        verify(mockSchedulingRepository).saveOptOutJobRecord(
            optOutJobRecord2.copy(
                status = OptOutJobStatus.REMOVED,
                optOutRemovedDateInMillis = TEST_CURRENT_TIME,
            ),
        )
    }

    @Test
    fun whenMarkRemovedProfilesWithRemovedProfileButNoJobRecordThenSkipsThatProfile() = runTest {
        // Setup: stored profile exists but no job record for it
        val storedProfiles = listOf(testExtractedProfile1)
        val newProfiles = emptyList<ExtractedProfile>()

        whenever(mockRepository.getExtractedProfiles(testBrokerName, testProfileQueryId))
            .thenReturn(storedProfiles)
        whenever(mockSchedulingRepository.getValidOptOutJobRecord(100L))
            .thenReturn(null) // No job record exists

        toTest.markRemovedOptOutJobRecords(newProfiles, testBrokerName, testProfileQueryId)

        verify(mockSchedulingRepository).getValidOptOutJobRecord(100L)
        // Should not call saveOptOutJobRecord since no job record exists
        verify(mockSchedulingRepository, never()).saveOptOutJobRecord(any())
    }

    companion object {
        private const val TEST_CURRENT_TIME = 5000L
    }
}
