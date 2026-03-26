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

package com.duckduckgo.pir.impl.email

import android.content.Context
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.EmailData
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.JobAttemptData
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.LinkFetchData
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.scheduling.JobRecordUpdater
import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirRepository.EmailConfirmationLinkFetchStatus
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.duckduckgo.pir.impl.store.db.EmailConfirmationEventType
import com.duckduckgo.pir.impl.store.db.EventType
import com.duckduckgo.pir.impl.store.db.PirEventLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealPirEmailConfirmationJobsRunnerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirEmailConfirmationJobsRunner

    private val mockPirSchedulingRepository: PirSchedulingRepository = mock()
    private val mockPirRepository: PirRepository = mock()
    private val mockJobRecordUpdater: JobRecordUpdater = mock()
    private val mockEmailConfirmation: PirEmailConfirmation = mock()
    private val mockPirPixelSender: PirPixelSender = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockPirEventsRepository: PirEventsRepository = mock()
    private val mockContext: Context = mock()

    @Before
    fun setUp() {
        testee = RealPirEmailConfirmationJobsRunner(
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pirSchedulingRepository = mockPirSchedulingRepository,
            pirRepository = mockPirRepository,
            jobRecordUpdater = mockJobRecordUpdater,
            emailConfirmation = mockEmailConfirmation,
            pirPixelSender = mockPirPixelSender,
            currentTimeProvider = mockCurrentTimeProvider,
            pirEventsRepository = mockPirEventsRepository,
        )
    }

    private val testCurrentTime = 1000L
    private val testBrokerName = "test-broker"
    private val testBrokerName2 = "test-broker-2"
    private val testBroker = Broker(
        name = testBrokerName,
        fileName = "test-broker.json",
        url = "https://test-broker.com",
        version = "1.0",
        parent = null,
        addedDatetime = 0L,
        removedAt = 0L,
    )
    private val testBroker2 = Broker(
        name = testBrokerName2,
        fileName = "test-broker-2.json",
        url = "https://test-broker-2.com",
        version = "1.0",
        parent = null,
        addedDatetime = 0L,
        removedAt = 0L,
    )

    private val testEmailData = EmailData(
        email = "test@example.com",
        attemptId = "attempt-123",
    )

    private val testEmailConfirmationJobRecord = EmailConfirmationJobRecord(
        brokerName = testBrokerName,
        userProfileId = 123L,
        extractedProfileId = 456L,
        emailData = testEmailData,
    )

    private val testEmailConfirmationJobRecordWithLink = EmailConfirmationJobRecord(
        brokerName = testBrokerName,
        userProfileId = 123L,
        extractedProfileId = 456L,
        emailData = testEmailData,
        linkFetchData = LinkFetchData(
            emailConfirmationLink = "https://example.com/confirm",
            linkFetchAttemptCount = 1,
            lastLinkFetchDateInMillis = testCurrentTime,
        ),
    )

    private val testEmailConfirmationJobRecordMaxedOut = EmailConfirmationJobRecord(
        brokerName = testBrokerName,
        userProfileId = 123L,
        extractedProfileId = 456L,
        emailData = testEmailData,
        linkFetchData = LinkFetchData(
            emailConfirmationLink = "https://example.com/confirm",
        ),
        jobAttemptData = JobAttemptData(
            jobAttemptCount = 3,
            lastJobAttemptDateInMillis = testCurrentTime,
            lastJobAttemptActionId = "action-123",
        ),
    )

    @Test
    fun whenNoActiveBrokersThenCompletesQuickly() = runTest {
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockPirPixelSender).reportEmailConfirmationStarted()
        verify(mockPirPixelSender).reportEmailConfirmationCompleted(
            totalTimeInMillis = 0L,
            totalFetchAttempts = 0,
            totalEmailConfirmationJobs = 0,
        )
        verify(mockPirEventsRepository).saveEventLog(
            PirEventLog(
                eventTimeInMillis = testCurrentTime,
                eventType = EventType.EMAIL_CONFIRMATION_STARTED,
            ),
        )
        verify(mockPirEventsRepository).saveEventLog(
            PirEventLog(
                eventTimeInMillis = testCurrentTime,
                eventType = EventType.EMAIL_CONFIRMATION_COMPLETED,
            ),
        )
        verifyNoInteractions(mockPirSchedulingRepository)
        verifyNoInteractions(mockJobRecordUpdater)
        verifyNoInteractions(mockEmailConfirmation)
    }

    @Test
    fun whenNoEligibleJobsWithNoLinkThenSkipsFetch() = runTest {
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(emptyList())

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockPirPixelSender).reportEmailConfirmationStarted()
        verify(mockPirPixelSender).reportEmailConfirmationCompleted(
            totalTimeInMillis = 0L,
            totalFetchAttempts = 0,
            totalEmailConfirmationJobs = 0,
        )
        verify(mockPirSchedulingRepository).getEmailConfirmationJobsWithNoLink()
        verify(mockPirSchedulingRepository).getEmailConfirmationJobsWithLink()
        verify(mockPirRepository, never()).getEmailConfirmationLinkStatus(any())
    }

    @Test
    fun whenJobsWithNoLinkExistButBrokerNotActiveThenSkipsFetch() = runTest {
        val inactiveBrokerJob = testEmailConfirmationJobRecord.copy(brokerName = "inactive-broker")
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(listOf(inactiveBrokerJob))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(emptyList())

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockPirRepository, never()).getEmailConfirmationLinkStatus(any())
    }

    @Test
    fun whenLinkFetchStatusIsReadyThenUpdatesJobRecordAndReportsPixel() = runTest {
        val testLink = "https://example.com/confirm"
        val emailReceivedAtMs = testCurrentTime - 5000L
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(
            listOf(testEmailConfirmationJobRecord),
        )
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(emptyList())
        whenever(mockPirRepository.getEmailConfirmationLinkStatus(listOf(testEmailData))).thenReturn(
            mapOf(
                testEmailData to EmailConfirmationLinkFetchStatus.Ready(
                    emailReceivedAtMs = emailReceivedAtMs,
                    data = mapOf("link" to testLink),
                ),
            ),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockJobRecordUpdater).recordEmailConfirmationFetchAttempt(testEmailConfirmationJobRecord.extractedProfileId)
        verify(mockJobRecordUpdater).markEmailConfirmationWithLink(
            testEmailConfirmationJobRecord.extractedProfileId,
            testLink,
        )
        verify(mockPirPixelSender).reportEmailConfirmationLinkFetched(
            brokerUrl = testBroker.url,
            brokerVersion = testBroker.version,
            linkAgeMs = 5000L,
        )
        verify(mockPirRepository).deleteEmailData(listOf(testEmailData))
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.LINK_FETCH_ATTEMPT,
            detail = "1",
        )
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.LINK_FETCH_READY,
            detail = "1",
        )
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.LINK_FETCH_ERROR,
            detail = "0",
        )
    }

    @Test
    fun whenLinkFetchStatusIsReadyButNoLinkKeyThenDoesNotUpdate() = runTest {
        val emailReceivedAtMs = testCurrentTime - 5000L
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(
            listOf(testEmailConfirmationJobRecord),
        )
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(emptyList())
        whenever(mockPirRepository.getEmailConfirmationLinkStatus(listOf(testEmailData))).thenReturn(
            mapOf(
                testEmailData to EmailConfirmationLinkFetchStatus.Ready(
                    emailReceivedAtMs = emailReceivedAtMs,
                    data = mapOf("other_key" to "value"),
                ),
            ),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockJobRecordUpdater).recordEmailConfirmationFetchAttempt(testEmailConfirmationJobRecord.extractedProfileId)
        verify(mockJobRecordUpdater, never()).markEmailConfirmationWithLink(any(), any())
        verify(mockPirPixelSender, never()).reportEmailConfirmationLinkFetched(any(), any(), any())
        verify(mockPirRepository, never()).deleteEmailData(any())
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.LINK_FETCH_READY,
            detail = "0",
        )
    }

    @Test
    fun whenLinkFetchStatusIsErrorThenMarksFailedAndReportsPixel() = runTest {
        val errorStatus = "404"
        val errorCode = "NOT_FOUND"
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(
            listOf(testEmailConfirmationJobRecord),
        )
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(emptyList())
        whenever(mockPirRepository.getEmailConfirmationLinkStatus(listOf(testEmailData))).thenReturn(
            mapOf(
                testEmailData to EmailConfirmationLinkFetchStatus.Error(
                    error = errorStatus,
                    errorCode = errorCode,
                ),
            ),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockJobRecordUpdater).recordEmailConfirmationFetchAttempt(testEmailConfirmationJobRecord.extractedProfileId)
        verify(mockJobRecordUpdater).markEmailConfirmationLinkFetchFailed(testEmailConfirmationJobRecord.extractedProfileId)
        verify(mockPirPixelSender).reportEmailConfirmationLinkFetchBEError(
            brokerUrl = testBroker.url,
            brokerVersion = testBroker.version,
            status = EmailConfirmationLinkFetchStatus.STATUS_ERROR,
            errorCode = errorCode,
        )
        verify(mockPirRepository).deleteEmailData(listOf(testEmailData))
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.LINK_FETCH_ERROR,
            detail = "1",
        )
    }

    @Test
    fun whenLinkFetchStatusIsUnknownThenMarksFailedAndReportsPixel() = runTest {
        val errorCode = "UNKNOWN_ERROR"
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(
            listOf(testEmailConfirmationJobRecord),
        )
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(emptyList())
        whenever(mockPirRepository.getEmailConfirmationLinkStatus(listOf(testEmailData))).thenReturn(
            mapOf(
                testEmailData to EmailConfirmationLinkFetchStatus.Unknown(
                    errorCode = errorCode,
                ),
            ),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockJobRecordUpdater).markEmailConfirmationLinkFetchFailed(testEmailConfirmationJobRecord.extractedProfileId)
        verify(mockPirPixelSender).reportEmailConfirmationLinkFetchBEError(
            brokerUrl = testBroker.url,
            brokerVersion = testBroker.version,
            status = PirRepository.EmailConfirmationLinkFetchStatus.STATUS_UNKNOWN,
            errorCode = errorCode,
        )
        verify(mockPirRepository, never()).deleteEmailData(listOf(testEmailData))
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.LINK_FETCH_ERROR,
            detail = "1",
        )
    }

    @Test
    fun whenLinkFetchStatusIsPendingThenDoesNothing() = runTest {
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(
            listOf(testEmailConfirmationJobRecord),
        )
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(emptyList())
        whenever(mockPirRepository.getEmailConfirmationLinkStatus(listOf(testEmailData))).thenReturn(
            mapOf(
                testEmailData to EmailConfirmationLinkFetchStatus.Pending,
            ),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockJobRecordUpdater, never()).markEmailConfirmationWithLink(any(), any())
        verify(mockJobRecordUpdater, never()).markEmailConfirmationLinkFetchFailed(any())
        verify(mockPirPixelSender, never()).reportEmailConfirmationLinkFetched(any(), any(), any())
        verify(mockPirPixelSender, never()).reportEmailConfirmationLinkFetchBEError(any(), any(), any(), any())
        verify(mockPirRepository, never()).deleteEmailData(any())
    }

    @Test
    fun whenMultipleJobsWithDifferentStatusesThenHandlesEachCorrectly() = runTest {
        val job1 = testEmailConfirmationJobRecord.copy(
            extractedProfileId = 100L,
            emailData = EmailData("email1@example.com", "attempt-1"),
        )
        val job2 = testEmailConfirmationJobRecord.copy(
            extractedProfileId = 200L,
            emailData = EmailData("email2@example.com", "attempt-2"),
        )
        val job3 = testEmailConfirmationJobRecord.copy(
            extractedProfileId = 300L,
            emailData = EmailData("email3@example.com", "attempt-3"),
        )

        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(
            listOf(job1, job2, job3),
        )
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(emptyList())
        whenever(mockPirRepository.getEmailConfirmationLinkStatus(any())).thenReturn(
            mapOf(
                job1.emailData to EmailConfirmationLinkFetchStatus.Ready(
                    emailReceivedAtMs = testCurrentTime,
                    data = mapOf("link" to "https://example.com/confirm1"),
                ),
                job2.emailData to EmailConfirmationLinkFetchStatus.Error("500", "SERVER_ERROR"),
                job3.emailData to EmailConfirmationLinkFetchStatus.Pending,
            ),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockJobRecordUpdater).markEmailConfirmationWithLink(100L, "https://example.com/confirm1")
        verify(mockJobRecordUpdater).markEmailConfirmationLinkFetchFailed(200L)
        verify(mockPirRepository).deleteEmailData(listOf(job1.emailData, job2.emailData))
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.LINK_FETCH_READY,
            detail = "1",
        )
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.LINK_FETCH_ERROR,
            detail = "1",
        )
    }

    @Test
    fun whenNoJobsWithLinkThenSkipsEmailConfirmationExecution() = runTest {
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(emptyList())

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockEmailConfirmation, never()).executeForEmailConfirmationJobs(any(), any(), any())
    }

    @Test
    fun whenJobsWithLinkExistButBrokerNotActiveThenSkipsExecution() = runTest {
        val inactiveBrokerJob = testEmailConfirmationJobRecordWithLink.copy(brokerName = "inactive-broker")
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(
            listOf(inactiveBrokerJob),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockEmailConfirmation, never()).executeForEmailConfirmationJobs(any(), any(), any())
    }

    @Test
    fun whenJobsReachedMaxAttemptsThenMarksAsMaxedOutAndReportsPixel() = runTest {
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(
            listOf(testEmailConfirmationJobRecordMaxedOut),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockJobRecordUpdater).recordEmailConfirmationAttemptMaxed(
            testEmailConfirmationJobRecordMaxedOut.extractedProfileId,
        )
        verify(mockPirPixelSender).reportEmailConfirmationAttemptRetriesExceeded(
            brokerUrl = testBroker.url,
            brokerVersion = testBroker.version,
            actionId = testEmailConfirmationJobRecordMaxedOut.jobAttemptData.lastJobAttemptActionId,
        )
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.EMAIL_CONFIRMATION_MAXED_OUT,
            detail = "1",
        )
        verify(mockEmailConfirmation, never()).executeForEmailConfirmationJobs(any(), any(), any())
    }

    @Test
    fun whenEligibleJobsWithLinkExistThenExecutesEmailConfirmation() = runTest {
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(
            listOf(testEmailConfirmationJobRecordWithLink),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockEmailConfirmation).executeForEmailConfirmationJobs(
            listOf(testEmailConfirmationJobRecordWithLink),
            mockContext,
            RunType.EMAIL_CONFIRMATION,
        )
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.EMAIL_CONFIRMATION_ATTEMPT,
            detail = "1",
        )
        verify(mockPirPixelSender).reportEmailConfirmationCompleted(
            totalTimeInMillis = 0L,
            totalFetchAttempts = 0,
            totalEmailConfirmationJobs = 1,
        )
    }

    @Test
    fun whenMultipleEligibleJobsThenExecutesAll() = runTest {
        val job1 = testEmailConfirmationJobRecordWithLink.copy(extractedProfileId = 100L)
        val job2 = testEmailConfirmationJobRecordWithLink.copy(extractedProfileId = 200L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(
            listOf(job1, job2),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockEmailConfirmation).executeForEmailConfirmationJobs(
            listOf(job1, job2),
            mockContext,
            RunType.EMAIL_CONFIRMATION,
        )
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.EMAIL_CONFIRMATION_ATTEMPT,
            detail = "2",
        )
        verify(mockPirPixelSender).reportEmailConfirmationCompleted(
            totalTimeInMillis = 0L,
            totalFetchAttempts = 0,
            totalEmailConfirmationJobs = 2,
        )
    }

    @Test
    fun whenMixOfMaxedOutAndEligibleJobsThenOnlyExecutesEligible() = runTest {
        val maxedOutJob = testEmailConfirmationJobRecordWithLink.copy(
            extractedProfileId = 100L,
            jobAttemptData = JobAttemptData(jobAttemptCount = 3),
        )
        val eligibleJob = testEmailConfirmationJobRecordWithLink.copy(
            extractedProfileId = 200L,
            jobAttemptData = JobAttemptData(jobAttemptCount = 2),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(
            listOf(maxedOutJob, eligibleJob),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockJobRecordUpdater).recordEmailConfirmationAttemptMaxed(100L)
        verify(mockEmailConfirmation).executeForEmailConfirmationJobs(
            listOf(eligibleJob),
            mockContext,
            RunType.EMAIL_CONFIRMATION,
        )
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.EMAIL_CONFIRMATION_MAXED_OUT,
            detail = "1",
        )
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.EMAIL_CONFIRMATION_ATTEMPT,
            detail = "1",
        )
    }

    @Test
    fun whenCompleteRunWithFetchAndExecutionThenReportsCorrectStats() = runTest {
        val jobNoLink = testEmailConfirmationJobRecord.copy(extractedProfileId = 100L)
        val jobWithLink1 = testEmailConfirmationJobRecordWithLink.copy(extractedProfileId = 200L)
        val jobWithLink2 = testEmailConfirmationJobRecordWithLink.copy(extractedProfileId = 300L)

        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime, testCurrentTime + 5000L)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(listOf(jobNoLink))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(
            listOf(jobWithLink1, jobWithLink2),
        )
        whenever(mockPirRepository.getEmailConfirmationLinkStatus(listOf(jobNoLink.emailData))).thenReturn(
            mapOf(
                jobNoLink.emailData to EmailConfirmationLinkFetchStatus.Ready(
                    emailReceivedAtMs = testCurrentTime,
                    data = mapOf("link" to "https://example.com/confirm"),
                ),
            ),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockPirPixelSender).reportEmailConfirmationCompleted(
            totalTimeInMillis = 5000L,
            totalFetchAttempts = 1,
            totalEmailConfirmationJobs = 2,
        )
    }

    @Test
    fun whenStopThenCallsStopOnEmailConfirmation() {
        testee.stop()

        verify(mockEmailConfirmation).stop()
    }

    @Test
    fun whenMultipleBrokersThenProcessesJobsForAllActiveBrokers() = runTest {
        val job1 = testEmailConfirmationJobRecord.copy(
            brokerName = testBrokerName,
            extractedProfileId = 100L,
            emailData = EmailData("email1@example.com", "attempt-1"),
        )
        val job2 = testEmailConfirmationJobRecord.copy(
            brokerName = testBrokerName2,
            extractedProfileId = 200L,
            emailData = EmailData("email2@example.com", "attempt-2"),
        )

        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker, testBroker2))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(listOf(job1, job2))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(emptyList())
        whenever(mockPirRepository.getEmailConfirmationLinkStatus(any())).thenReturn(
            mapOf(
                job1.emailData to EmailConfirmationLinkFetchStatus.Ready(
                    emailReceivedAtMs = testCurrentTime,
                    data = mapOf("link" to "https://example.com/confirm1"),
                ),
                job2.emailData to EmailConfirmationLinkFetchStatus.Ready(
                    emailReceivedAtMs = testCurrentTime,
                    data = mapOf("link" to "https://example.com/confirm2"),
                ),
            ),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockJobRecordUpdater).recordEmailConfirmationFetchAttempt(100L)
        verify(mockJobRecordUpdater).recordEmailConfirmationFetchAttempt(200L)
        verify(mockJobRecordUpdater).markEmailConfirmationWithLink(100L, "https://example.com/confirm1")
        verify(mockJobRecordUpdater).markEmailConfirmationWithLink(200L, "https://example.com/confirm2")
        verify(mockPirPixelSender).reportEmailConfirmationLinkFetched(
            brokerUrl = testBroker.url,
            brokerVersion = testBroker.version,
            linkAgeMs = 0L,
        )
        verify(mockPirPixelSender).reportEmailConfirmationLinkFetched(
            brokerUrl = testBroker2.url,
            brokerVersion = testBroker2.version,
            linkAgeMs = 0L,
        )
    }

    @Test
    fun whenLinkFetchReturnsJobRecordNotFoundThenSkipsProcessing() = runTest {
        val unknownEmailData = EmailData("unknown@example.com", "unknown-attempt")
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(
            listOf(testEmailConfirmationJobRecord),
        )
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(emptyList())
        whenever(mockPirRepository.getEmailConfirmationLinkStatus(listOf(testEmailData))).thenReturn(
            mapOf(
                unknownEmailData to EmailConfirmationLinkFetchStatus.Ready(
                    emailReceivedAtMs = testCurrentTime,
                    data = mapOf("link" to "https://example.com/confirm"),
                ),
            ),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockJobRecordUpdater, never()).markEmailConfirmationWithLink(any(), any())
        verify(mockPirPixelSender, never()).reportEmailConfirmationLinkFetched(any(), any(), any())
    }

    @Test
    fun whenJobsWithLinkButAllMaxedOutThenNoExecutionOccurs() = runTest {
        // Given
        val maxedJob1 = testEmailConfirmationJobRecordWithLink.copy(
            extractedProfileId = 100L,
            jobAttemptData = JobAttemptData(jobAttemptCount = 3),
        )
        val maxedJob2 = testEmailConfirmationJobRecordWithLink.copy(
            extractedProfileId = 200L,
            jobAttemptData = JobAttemptData(jobAttemptCount = 4),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(
            listOf(maxedJob1, maxedJob2),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockJobRecordUpdater).recordEmailConfirmationAttemptMaxed(100L)
        verify(mockJobRecordUpdater).recordEmailConfirmationAttemptMaxed(200L)
        verify(mockEmailConfirmation, never()).executeForEmailConfirmationJobs(any(), any(), any())
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.EMAIL_CONFIRMATION_MAXED_OUT,
            detail = "2",
        )
        verify(mockPirPixelSender).reportEmailConfirmationCompleted(
            totalTimeInMillis = 0L,
            totalFetchAttempts = 0,
            totalEmailConfirmationJobs = 0,
        )
    }

    @Test
    fun whenJobAttemptCountLessThanMaxThenJobIsEligible() = runTest {
        val job0Attempts = testEmailConfirmationJobRecordWithLink.copy(
            extractedProfileId = 100L,
            jobAttemptData = JobAttemptData(jobAttemptCount = 0),
        )
        val job1Attempt = testEmailConfirmationJobRecordWithLink.copy(
            extractedProfileId = 200L,
            jobAttemptData = JobAttemptData(jobAttemptCount = 1),
        )
        val job2Attempts = testEmailConfirmationJobRecordWithLink.copy(
            extractedProfileId = 300L,
            jobAttemptData = JobAttemptData(jobAttemptCount = 2),
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(emptyList())
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(
            listOf(job0Attempts, job1Attempt, job2Attempts),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockEmailConfirmation).executeForEmailConfirmationJobs(
            listOf(job0Attempts, job1Attempt, job2Attempts),
            mockContext,
            RunType.EMAIL_CONFIRMATION,
        )
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.EMAIL_CONFIRMATION_ATTEMPT,
            detail = "3",
        )
    }

    @Test
    fun whenNoEmailDataToDeleteThenSkipsDeletion() = runTest {
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(
            listOf(testEmailConfirmationJobRecord),
        )
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(emptyList())
        whenever(mockPirRepository.getEmailConfirmationLinkStatus(listOf(testEmailData))).thenReturn(
            mapOf(
                testEmailData to EmailConfirmationLinkFetchStatus.Pending,
            ),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockPirRepository, never()).deleteEmailData(any())
    }

    @Test
    fun whenFetchAttemptsRecordedThenCountIsAccurate() = runTest {
        val job1 = testEmailConfirmationJobRecord.copy(
            extractedProfileId = 100L,
            emailData = EmailData("email1@example.com", "attempt-1"),
        )
        val job2 = testEmailConfirmationJobRecord.copy(
            extractedProfileId = 200L,
            emailData = EmailData("email2@example.com", "attempt-2"),
        )
        val job3 = testEmailConfirmationJobRecord.copy(
            extractedProfileId = 300L,
            emailData = EmailData("email3@example.com", "attempt-3"),
        )

        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(listOf(testBroker))
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithNoLink()).thenReturn(
            listOf(job1, job2, job3),
        )
        whenever(mockPirSchedulingRepository.getEmailConfirmationJobsWithLink()).thenReturn(emptyList())
        whenever(mockPirRepository.getEmailConfirmationLinkStatus(any())).thenReturn(
            mapOf(
                job1.emailData to EmailConfirmationLinkFetchStatus.Pending,
                job2.emailData to EmailConfirmationLinkFetchStatus.Pending,
                job3.emailData to EmailConfirmationLinkFetchStatus.Pending,
            ),
        )

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockJobRecordUpdater).recordEmailConfirmationFetchAttempt(100L)
        verify(mockJobRecordUpdater).recordEmailConfirmationFetchAttempt(200L)
        verify(mockJobRecordUpdater).recordEmailConfirmationFetchAttempt(300L)
        verify(mockPirEventsRepository).saveEmailConfirmationLog(
            eventTimeInMillis = testCurrentTime,
            type = EmailConfirmationEventType.LINK_FETCH_ATTEMPT,
            detail = "3",
        )
        verify(mockPirPixelSender).reportEmailConfirmationCompleted(
            totalTimeInMillis = eq(0L),
            totalFetchAttempts = eq(3),
            totalEmailConfirmationJobs = eq(0),
        )
    }

    @Test
    fun whenEventLogsAreSavedThenTimestampsAreCorrect() = runTest {
        val startTime = 1000L
        val endTime = 6000L
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(startTime, endTime)
        whenever(mockPirRepository.getAllActiveBrokerObjects()).thenReturn(emptyList())

        val result = testee.runEligibleJobs(mockContext)

        assertTrue(result.isSuccess)
        verify(mockPirEventsRepository).saveEventLog(
            PirEventLog(
                eventTimeInMillis = startTime,
                eventType = EventType.EMAIL_CONFIRMATION_STARTED,
            ),
        )
        verify(mockPirEventsRepository).saveEventLog(
            PirEventLog(
                eventTimeInMillis = endTime,
                eventType = EventType.EMAIL_CONFIRMATION_COMPLETED,
            ),
        )
        verify(mockPirPixelSender).reportEmailConfirmationCompleted(
            totalTimeInMillis = 5000L,
            totalFetchAttempts = 0,
            totalEmailConfirmationJobs = 0,
        )
    }
}
