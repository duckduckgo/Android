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

import android.content.Context
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.PirConstants.DEFAULT_PROFILE_QUERIES
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.ScanJobRecord.ScanJobStatus
import com.duckduckgo.pir.impl.optout.PirOptOut
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.scan.PirScan
import com.duckduckgo.pir.impl.scheduling.PirExecutionType.MANUAL
import com.duckduckgo.pir.impl.scheduling.PirExecutionType.SCHEDULED
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealPirJobsRunnerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirJobsRunner

    private val mockPirRepository: PirRepository = mock()
    private val mockPirSchedulingRepository: PirSchedulingRepository = mock()
    private val mockEligibleOptOutJobProvider: EligibleOptOutJobProvider = mock()
    private val mockEligibleScanJobProvider: EligibleScanJobProvider = mock()
    private val mockPirScan: PirScan = mock()
    private val mockPirOptOut: PirOptOut = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockContext: Context = mock()
    private val mockPixelSender: PirPixelSender = mock()

    @Before
    fun setUp() {
        testee = RealPirJobsRunner(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pirRepository = mockPirRepository,
            pirSchedulingRepository = mockPirSchedulingRepository,
            eligibleOptOutJobProvider = mockEligibleOptOutJobProvider,
            eligibleScanJobProvider = mockEligibleScanJobProvider,
            pirScan = mockPirScan,
            pirOptOut = mockPirOptOut,
            currentTimeProvider = mockCurrentTimeProvider,
            pixelSender = mockPixelSender,
        )
    }

    // Test data
    private val testCurrentTime = 1000L
    private val testBrokerName = "test-broker"
    private val testBrokerName2 = "test-broker-2"
    private val testActiveBrokers = listOf(testBrokerName, testBrokerName2)
    private val testProfileQuery = ProfileQuery(
        id = 123L,
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
    private val testDeprecatedProfileQuery = ProfileQuery(
        id = 456L,
        firstName = "Jane",
        lastName = "Smith",
        city = "Chicago",
        state = "IL",
        addresses = emptyList(),
        birthYear = 1985,
        fullName = "Jane Smith",
        age = 38,
        deprecated = true,
    )
    private val testUserProfileQueries = listOf(testProfileQuery, testDeprecatedProfileQuery)

    private val testScanJobRecord = ScanJobRecord(
        brokerName = testBrokerName,
        userProfileId = testProfileQuery.id,
        status = ScanJobStatus.NOT_EXECUTED,
        lastScanDateInMillis = 0L,
    )

    private val testExtractedProfile = ExtractedProfile(
        dbId = 789L,
        profileQueryId = testProfileQuery.id,
        brokerName = testBrokerName,
        name = "John Doe",
        alternativeNames = emptyList(),
        age = "33",
        addresses = emptyList(),
        phoneNumbers = emptyList(),
        relatives = emptyList(),
        reportId = "report123",
        email = "john@example.com",
        fullName = "John Doe",
        profileUrl = "https://example.com/profile",
        identifier = "id123",
    )

    private val testOptOutJobRecord = OptOutJobRecord(
        extractedProfileId = testExtractedProfile.dbId,
        brokerName = testBrokerName,
        userProfileId = testProfileQuery.id,
        status = OptOutJobStatus.NOT_EXECUTED,
        attemptCount = 0,
        lastOptOutAttemptDateInMillis = 0L,
        optOutRequestedDateInMillis = testCurrentTime,
        optOutRemovedDateInMillis = 0L,
    )

    @Test
    fun whenEmptyActiveBrokersAndEmptyProfileQueriesThenCompleteQuick() = runTest {
        // Given
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(emptyList())

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPixelSender).reportManualScanStarted()
        verify(mockPixelSender).reportScanStats(0)
        verify(mockPixelSender).reportOptOutStats(0)
        verify(mockPixelSender).reportManualScanCompleted(any())
        verify(mockPirScan).stop()
        verifyNoInteractions(mockPirSchedulingRepository)
        verifyNoInteractions(mockEligibleScanJobProvider)
        verifyNoInteractions(mockEligibleOptOutJobProvider)
        verifyNoInteractions(mockPirOptOut)
    }

    @Test
    fun whenEmptyProfileQueriesUsesDefaultProfileQueries() = runTest {
        // Given
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(testActiveBrokers)
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(emptyList())
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(emptyList())
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                DEFAULT_PROFILE_QUERIES[0].id,
            ),
        ).thenReturn(null)
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName2,
                DEFAULT_PROFILE_QUERIES[0].id,
            ),
        ).thenReturn(null)
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                DEFAULT_PROFILE_QUERIES[1].id,
            ),
        ).thenReturn(null)
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName2,
                DEFAULT_PROFILE_QUERIES[1].id,
            ),
        ).thenReturn(null)
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                DEFAULT_PROFILE_QUERIES[2].id,
            ),
        ).thenReturn(null)
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName2,
                DEFAULT_PROFILE_QUERIES[2].id,
            ),
        ).thenReturn(null)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        val result = testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        assertTrue(result.isSuccess)
        // Should create scan jobs for all active brokers and all default profiles
        verify(mockPirSchedulingRepository).saveScanJobRecords(
            listOf(
                ScanJobRecord(
                    brokerName = testBrokerName,
                    userProfileId = DEFAULT_PROFILE_QUERIES[0].id,
                ),
                ScanJobRecord(
                    brokerName = testBrokerName2,
                    userProfileId = DEFAULT_PROFILE_QUERIES[0].id,
                ),
                ScanJobRecord(
                    brokerName = testBrokerName,
                    userProfileId = DEFAULT_PROFILE_QUERIES[1].id,
                ),
                ScanJobRecord(
                    brokerName = testBrokerName2,
                    userProfileId = DEFAULT_PROFILE_QUERIES[1].id,
                ),
                ScanJobRecord(
                    brokerName = testBrokerName,
                    userProfileId = DEFAULT_PROFILE_QUERIES[2].id,
                ),
                ScanJobRecord(
                    brokerName = testBrokerName2,
                    userProfileId = DEFAULT_PROFILE_QUERIES[2].id,
                ),
            ),
        )
    }

    @Test
    fun whenEmptyActiveBrokersThenCompletesQuick() = runTest {
        // Given
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPixelSender).reportManualScanStarted()
        verify(mockPixelSender).reportScanStats(0)
        verify(mockPixelSender).reportOptOutStats(0)
        verify(mockPixelSender).reportManualScanCompleted(any())
        verify(mockPirScan).stop()
        verifyNoInteractions(mockPirSchedulingRepository)
        verifyNoInteractions(mockEligibleScanJobProvider)
        verifyNoInteractions(mockEligibleOptOutJobProvider)
        verifyNoInteractions(mockPirOptOut)
    }

    @Test
    fun whenBrokerIsNotActiveThenExecutesScanJobsForValidBrokersOnly() = runTest {
        // Given
        val invalidBrokerJobRecord = testScanJobRecord.copy(brokerName = testBrokerName2)
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(emptyList())
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                testProfileQuery.id,
            ),
        ).thenReturn(testScanJobRecord)
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName2,
                testProfileQuery.id,
            ),
        ).thenReturn(invalidBrokerJobRecord)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            listOf(testScanJobRecord, invalidBrokerJobRecord),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(
            mockPirScan.executeScanForJobs(
                listOf(testScanJobRecord),
                mockContext,
                RunType.MANUAL,
            ),
        ).thenReturn(Result.success(Unit))

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPixelSender).reportManualScanStarted()
        verify(mockPixelSender).reportManualScanCompleted(any())
        verify(mockPixelSender).reportScanStats(1)
        verify(mockPixelSender).reportOptOutStats(0)
        verify(mockPirScan).executeScanForJobs(
            listOf(testScanJobRecord),
            mockContext,
            RunType.MANUAL,
        )
    }

    @Test
    fun whenManualExecutionTypeThenExecutesScanJobsWithManualRunType() = runTest {
        // Given
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(testActiveBrokers)
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(emptyList())
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                testProfileQuery.id,
            ),
        ).thenReturn(testScanJobRecord)
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName2,
                testProfileQuery.id,
            ),
        ).thenReturn(testScanJobRecord)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            listOf(testScanJobRecord),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(
            mockPirScan.executeScanForJobs(
                listOf(testScanJobRecord),
                mockContext,
                RunType.MANUAL,
            ),
        ).thenReturn(Result.success(Unit))

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPixelSender).reportManualScanStarted()
        verify(mockPixelSender).reportManualScanCompleted(any())
        verify(mockPixelSender).reportScanStats(1)
        verify(mockPixelSender).reportOptOutStats(0)
        verify(mockPirScan).executeScanForJobs(
            listOf(testScanJobRecord),
            mockContext,
            RunType.MANUAL,
        )
    }

    @Test
    fun whenScheduledExecutionTypeThenExecutesScanJobsWithScheduledRunType() = runTest {
        // Given
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(testActiveBrokers)
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(testUserProfileQueries)
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(emptyList())
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                testProfileQuery.id,
            ),
        ).thenReturn(testScanJobRecord)
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName2,
                testProfileQuery.id,
            ),
        ).thenReturn(testScanJobRecord)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            listOf(testScanJobRecord),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(
            mockPirScan.executeScanForJobs(
                listOf(testScanJobRecord),
                mockContext,
                RunType.SCHEDULED,
            ),
        ).thenReturn(Result.success(Unit))

        // When
        testee.runEligibleJobs(mockContext, SCHEDULED)

        // Then
        verify(mockPixelSender).reportScheduledScanStarted()
        verify(mockPixelSender).reportScheduledScanCompleted(any())
        verify(mockPixelSender).reportScanStats(1)
        verify(mockPixelSender).reportOptOutStats(0)
        verify(mockPirScan).executeScanForJobs(
            listOf(testScanJobRecord),
            mockContext,
            RunType.SCHEDULED,
        )
    }

    @Test
    fun whenValidScanJobRecordExistsThenDoesNotCreateNewScanJob() = runTest {
        // Given
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(emptyList())
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                testProfileQuery.id,
            ),
        ).thenReturn(testScanJobRecord)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPirSchedulingRepository, never()).saveScanJobRecords(any())
    }

    @Test
    fun whenNoValidScanJobRecordExistsThenCreatesNewScanJob() = runTest {
        // Given
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(emptyList())
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                testProfileQuery.id,
            ),
        ).thenReturn(null)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPirSchedulingRepository).saveScanJobRecords(
            listOf(ScanJobRecord(brokerName = testBrokerName, userProfileId = testProfileQuery.id)),
        )
    }

    @Test
    fun whenDeprecatedProfileQueryThenDoesNotCreateScanJob() = runTest {
        // Given
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(emptyList())
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(
            listOf(
                testDeprecatedProfileQuery,
            ),
        )
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPirSchedulingRepository, never()).saveScanJobRecords(any())
    }

    @Test
    fun whenActiveBrokerIsNotFormOptOutThenDoNotCreateJob() = runTest {
        // Given
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(listOf("some-other-broker"))
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                testProfileQuery.id,
            ),
        ).thenReturn(testScanJobRecord)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(testExtractedProfile))
        whenever(mockPirSchedulingRepository.getValidOptOutJobRecord(testExtractedProfile.dbId, includeDeprecated = true)).thenReturn(
            null,
        )
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPirScan).stop()
        verify(mockPixelSender).reportManualScanStarted()
        verify(mockPixelSender).reportManualScanCompleted(any())
        verify(mockPixelSender).reportOptOutStats(0)
        verify(mockPirSchedulingRepository, never()).saveOptOutJobRecords(
            listOf(
                OptOutJobRecord(
                    extractedProfileId = testExtractedProfile.dbId,
                    brokerName = testExtractedProfile.brokerName,
                    userProfileId = testExtractedProfile.profileQueryId,
                ),
            ),
        )
    }

    @Test
    fun whenExtractedProfilesExistThenCreatesOptOutJobs() = runTest {
        // Given
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                testProfileQuery.id,
            ),
        ).thenReturn(testScanJobRecord)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(testExtractedProfile))
        whenever(mockPirSchedulingRepository.getValidOptOutJobRecord(testExtractedProfile.dbId, includeDeprecated = true)).thenReturn(
            null,
        )
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPirSchedulingRepository).saveOptOutJobRecords(
            listOf(
                OptOutJobRecord(
                    extractedProfileId = testExtractedProfile.dbId,
                    brokerName = testExtractedProfile.brokerName,
                    userProfileId = testExtractedProfile.profileQueryId,
                ),
            ),
        )
    }

    @Test
    fun whenValidOptOutJobRecordExistsThenDoesNotCreateNewOptOutJob() = runTest {
        // Given
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                testProfileQuery.id,
            ),
        ).thenReturn(testScanJobRecord)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(testExtractedProfile))
        whenever(mockPirSchedulingRepository.getValidOptOutJobRecord(testExtractedProfile.dbId, includeDeprecated = true)).thenReturn(
            testOptOutJobRecord,
        )
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPirSchedulingRepository, never()).saveOptOutJobRecords(any())
    }

    @Test
    fun whenExtractedProfileBrokerNotActiveThenDoesNotCreateOptOutJob() = runTest {
        // Given
        val inactiveBrokerExtractedProfile =
            testExtractedProfile.copy(brokerName = "inactive-broker")
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(emptyList())
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                testProfileQuery.id,
            ),
        ).thenReturn(testScanJobRecord)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(
            listOf(
                inactiveBrokerExtractedProfile,
            ),
        )
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPirSchedulingRepository, never()).saveOptOutJobRecords(any())
    }

    @Test
    fun whenEligibleOptOutJobsExistAndBrokerDoesNotSupportFormOptOutThenDoesNothing() = runTest {
        // Given
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(listOf("some-other-broker"))
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                testProfileQuery.id,
            ),
        ).thenReturn(testScanJobRecord)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            listOf(testOptOutJobRecord),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(
            mockPirOptOut.executeOptOutForJobs(
                listOf(testOptOutJobRecord),
                mockContext,
            ),
        ).thenReturn(Result.success(Unit))

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPixelSender).reportManualScanStarted()
        verify(mockPixelSender).reportManualScanCompleted(any())
        verify(mockPixelSender).reportOptOutStats(0)
        verify(mockPirOptOut, never()).executeOptOutForJobs(listOf(testOptOutJobRecord), mockContext)
    }

    @Test
    fun whenEligibleOptOutJobsExistAndBrokerSupportsFormOptOutThenExecutesOptOut() = runTest {
        // Given
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                testProfileQuery.id,
            ),
        ).thenReturn(testScanJobRecord)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            listOf(testOptOutJobRecord),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(
            mockPirOptOut.executeOptOutForJobs(
                listOf(testOptOutJobRecord),
                mockContext,
            ),
        ).thenReturn(Result.success(Unit))

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPixelSender).reportOptOutStats(1)
        verify(mockPirOptOut).executeOptOutForJobs(listOf(testOptOutJobRecord), mockContext)
    }

    @Test
    fun whenEligibleOptOutJobsExistButBrokerDoesNotSupportFormOptOutThenDoesNotExecuteOptOut() =
        runTest {
            // Given
            whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
            whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
            whenever(
                mockPirSchedulingRepository.getValidScanJobRecord(
                    testBrokerName,
                    testProfileQuery.id,
                ),
            ).thenReturn(testScanJobRecord)
            whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
                emptyList(),
            )
            whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(emptyList())
            whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
                listOf(testOptOutJobRecord),
            )
            whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(emptyList())
            whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

            // When
            testee.runEligibleJobs(mockContext, MANUAL)

            // Then
            verify(mockPixelSender).reportOptOutStats(0)
            verify(mockPirOptOut, never()).executeOptOutForJobs(
                listOf(testOptOutJobRecord),
                mockContext,
            )
        }

    @Test
    fun whenRunEligibleJobsThenAllRequiredMethodsAreCalled() = runTest {
        // Given
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(listOf(testProfileQuery))
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                testProfileQuery.id,
            ),
        ).thenReturn(null)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            listOf(testScanJobRecord),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(listOf(testExtractedProfile))
        whenever(mockPirSchedulingRepository.getValidOptOutJobRecord(testExtractedProfile.dbId, includeDeprecated = true)).thenReturn(
            null,
        )
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            listOf(testOptOutJobRecord),
        )
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(listOf(testBrokerName))
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(
            mockPirScan.executeScanForJobs(
                listOf(testScanJobRecord),
                mockContext,
                RunType.MANUAL,
            ),
        ).thenReturn(Result.success(Unit))
        whenever(
            mockPirOptOut.executeOptOutForJobs(
                listOf(testOptOutJobRecord),
                mockContext,
            ),
        ).thenReturn(Result.success(Unit))

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        // Verify all major operations are called
        verify(mockPirScan).stop()
        verify(mockPirRepository).getAllActiveBrokers()
        verify(mockPirRepository).getAllUserProfileQueries()
        verify(mockPirSchedulingRepository).getValidScanJobRecord(
            testBrokerName,
            testProfileQuery.id,
        )
        verify(mockPirSchedulingRepository).saveScanJobRecords(
            listOf(ScanJobRecord(brokerName = testBrokerName, userProfileId = testProfileQuery.id)),
        )
        verify(mockEligibleScanJobProvider).getAllEligibleScanJobs(testCurrentTime)
        verify(mockPirScan).executeScanForJobs(
            listOf(testScanJobRecord),
            mockContext,
            RunType.MANUAL,
        )
        verify(mockPirRepository).getAllExtractedProfiles()
        verify(mockPirSchedulingRepository).getValidOptOutJobRecord(testExtractedProfile.dbId, includeDeprecated = true)
        verify(mockPirSchedulingRepository).saveOptOutJobRecords(
            listOf(
                OptOutJobRecord(
                    extractedProfileId = testExtractedProfile.dbId,
                    brokerName = testExtractedProfile.brokerName,
                    userProfileId = testExtractedProfile.profileQueryId,
                ),
            ),
        )
        verify(mockEligibleOptOutJobProvider).getAllEligibleOptOutJobs(testCurrentTime)
        verify(mockPirRepository).getBrokersForOptOut(true)
        verify(mockPixelSender).reportOptOutStats(1)
        verify(mockPirOptOut).executeOptOutForJobs(listOf(testOptOutJobRecord), mockContext)
    }

    @Test
    fun whenRunEligibleJobsThenStopsPirScanFirst() = runTest {
        // Given
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(emptyList())
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(emptyList())

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        verify(mockPirScan).stop()
    }

    @Test
    fun whenDeprecatedProfileWithExtractedProfilesThenCreatesOptOutJob() = runTest {
        // Given
        val extractedProfileForDeprecatedProfile = testExtractedProfile.copy(
            profileQueryId = testDeprecatedProfileQuery.id,
        )
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(
            listOf(testDeprecatedProfileQuery),
        )
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(
            listOf(extractedProfileForDeprecatedProfile),
        )
        whenever(mockPirSchedulingRepository.getValidOptOutJobRecord(extractedProfileForDeprecatedProfile.dbId, includeDeprecated = true)).thenReturn(
            null,
        )
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        // Should create opt-out job even for deprecated profile if it has extracted profiles
        verify(mockPirSchedulingRepository).saveOptOutJobRecords(
            listOf(
                OptOutJobRecord(
                    extractedProfileId = extractedProfileForDeprecatedProfile.dbId,
                    brokerName = extractedProfileForDeprecatedProfile.brokerName,
                    userProfileId = extractedProfileForDeprecatedProfile.profileQueryId,
                ),
            ),
        )
    }

    @Test
    fun whenDeprecatedProfileWithExtractedProfilesAndExistingOptOutJobThenDoesNotCreateNew() = runTest {
        // Given
        val extractedProfileForDeprecatedProfile = testExtractedProfile.copy(
            profileQueryId = testDeprecatedProfileQuery.id,
        )
        val existingOptOutJob = testOptOutJobRecord.copy(
            extractedProfileId = extractedProfileForDeprecatedProfile.dbId,
            userProfileId = testDeprecatedProfileQuery.id,
        )
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(
            listOf(testDeprecatedProfileQuery),
        )
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(
            listOf(extractedProfileForDeprecatedProfile),
        )
        whenever(mockPirSchedulingRepository.getValidOptOutJobRecord(extractedProfileForDeprecatedProfile.dbId, includeDeprecated = true)).thenReturn(
            existingOptOutJob,
        )
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        // Should not create new opt-out job if one already exists
        verify(mockPirSchedulingRepository, never()).saveOptOutJobRecords(any())
    }

    @Test
    fun whenMixOfDeprecatedAndNonDeprecatedProfilesThenCreatesJobsCorrectly() = runTest {
        // Given
        val extractedProfileForNormal = testExtractedProfile.copy(
            dbId = 100L,
            profileQueryId = testProfileQuery.id,
        )
        val extractedProfileForDeprecated = testExtractedProfile.copy(
            dbId = 200L,
            profileQueryId = testDeprecatedProfileQuery.id,
        )
        whenever(mockPirRepository.getBrokersForOptOut(true)).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllActiveBrokers()).thenReturn(listOf(testBrokerName))
        whenever(mockPirRepository.getAllUserProfileQueries()).thenReturn(
            listOf(testProfileQuery, testDeprecatedProfileQuery),
        )
        whenever(
            mockPirSchedulingRepository.getValidScanJobRecord(
                testBrokerName,
                testProfileQuery.id,
            ),
        ).thenReturn(testScanJobRecord)
        whenever(mockEligibleScanJobProvider.getAllEligibleScanJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockPirRepository.getAllExtractedProfiles()).thenReturn(
            listOf(extractedProfileForNormal, extractedProfileForDeprecated),
        )
        whenever(mockPirSchedulingRepository.getValidOptOutJobRecord(extractedProfileForNormal.dbId, includeDeprecated = true)).thenReturn(
            null,
        )
        whenever(mockPirSchedulingRepository.getValidOptOutJobRecord(extractedProfileForDeprecated.dbId, includeDeprecated = true)).thenReturn(
            null,
        )
        whenever(mockEligibleOptOutJobProvider.getAllEligibleOptOutJobs(testCurrentTime)).thenReturn(
            emptyList(),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)

        // When
        testee.runEligibleJobs(mockContext, MANUAL)

        // Then
        // Should create scan job only for non-deprecated profile
        verify(mockPirSchedulingRepository, never()).saveScanJobRecords(any())

        // Should create opt-out jobs for both profiles since both have extracted profiles
        verify(mockPirSchedulingRepository).saveOptOutJobRecords(
            listOf(
                OptOutJobRecord(
                    extractedProfileId = extractedProfileForNormal.dbId,
                    brokerName = extractedProfileForNormal.brokerName,
                    userProfileId = extractedProfileForNormal.profileQueryId,
                ),
                OptOutJobRecord(
                    extractedProfileId = extractedProfileForDeprecated.dbId,
                    brokerName = extractedProfileForDeprecated.brokerName,
                    userProfileId = extractedProfileForDeprecated.profileQueryId,
                ),
            ),
        )
    }

    @Test
    fun whenStopThenCallsStopOnPirScanAndPirOptOut() {
        // When
        testee.stop()

        // Then
        verify(mockPirScan).stop()
        verify(mockPirOptOut).stop()
    }
}
