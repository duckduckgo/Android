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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordEmailConfirmationCompleted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordEmailConfirmationNeeded
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordEmailConfirmationStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutFailed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutSubmitted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanActionSucceeded
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanFailed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanSuccess
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerStepInvalidEvent
import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.EmailData
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.JobAttemptData
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.LinkFetchData
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.OptOutJobRecord.OptOutJobStatus
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.pixels.PirStage.OTHER
import com.duckduckgo.pir.impl.scheduling.JobRecordUpdater
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExtractedResponse.ScriptAddressCityState
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExtractedResponse.ScriptExtractedProfile
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.impl.scripts.models.asActionType
import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.duckduckgo.pir.impl.store.db.BrokerScanEventType.BROKER_ERROR
import com.duckduckgo.pir.impl.store.db.BrokerScanEventType.BROKER_STARTED
import com.duckduckgo.pir.impl.store.db.BrokerScanEventType.BROKER_SUCCESS
import com.duckduckgo.pir.impl.store.db.EmailConfirmationEventType.EMAIL_CONFIRMATION_FAILED
import com.duckduckgo.pir.impl.store.db.EmailConfirmationEventType.EMAIL_CONFIRMATION_SUCCESS
import com.duckduckgo.pir.impl.store.db.PirBrokerScanLog
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class RealPirRunStateHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirRunStateHandler

    private val mockRepository: PirRepository = mock()
    private val mockEventsRepository: PirEventsRepository = mock()
    private val mockPixelSender: PirPixelSender = mock()
    private val mockJobRecordUpdater: JobRecordUpdater = mock()
    private val mockSchedulingRepository: PirSchedulingRepository = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val moshi: Moshi = Moshi.Builder().build()
    private val networkProtectionState: NetworkProtectionState = mock()

    @Before
    fun setUp() {
        testee =
            RealPirRunStateHandler(
                repository = mockRepository,
                eventsRepository = mockEventsRepository,
                pixelSender = mockPixelSender,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                jobRecordUpdater = mockJobRecordUpdater,
                pirSchedulingRepository = mockSchedulingRepository,
                currentTimeProvider = mockCurrentTimeProvider,
                moshi = moshi,
                networkProtectionState = networkProtectionState,
            )

        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testEventTimeInMillis)
    }

    // Test data
    private val testBrokerName = "test-broker"
    private val testProfileQueryId = 123L
    private val testStartTimeInMillis = 1000L
    private val testEventTimeInMillis = 2000L
    private val testTotalTimeMillis = 1000L
    private val testExtractedProfileId = 456L

    private val testExtractedProfile =
        ExtractedProfile(
            dbId = testExtractedProfileId,
            profileQueryId = testProfileQueryId,
            brokerName = testBrokerName,
            name = "John Doe",
            alternativeNames = listOf("Johnny", "J. Doe"),
            age = "30",
            addresses =
            listOf(
                AddressCityState(
                    city = "New York",
                    state = "NY",
                    fullAddress = "123 Main St",
                ),
            ),
            phoneNumbers = listOf("555-1234"),
            relatives = listOf("Jane Doe"),
            reportId = "report123",
            email = "john@example.com",
            fullName = "John Michael Doe",
            profileUrl = "https://example.com/profile/123",
            identifier = "id123",
        )

    private val testScriptExtractedProfile =
        ScriptExtractedProfile(
            name = "John Doe",
            alternativeNames = listOf("Johnny", "J. Doe"),
            age = "30",
            addresses =
            listOf(
                ScriptAddressCityState(
                    city = "New York",
                    state = "NY",
                    fullAddress = "123 Main St",
                ),
            ),
            phoneNumbers = listOf("555-1234"),
            relatives = listOf("Jane Doe"),
            profileUrl = "https://example.com/profile/123",
            identifier = "id123",
            reportId = "report123",
            email = "john@example.com",
            fullName = "John Michael Doe",
        )

    private val testEmailConfirmationJob =
        EmailConfirmationJobRecord(
            brokerName = testBrokerName,
            userProfileId = testProfileQueryId,
            extractedProfileId = testExtractedProfileId,
            emailData = EmailData(
                email = "john@example.com",
                attemptId = "c9982ded-021a-4251-9e03-2c58b130410f",
            ),
            linkFetchData = LinkFetchData(
                emailConfirmationLink = "https://example.com/confirm",
                linkFetchAttemptCount = 5,
                lastLinkFetchDateInMillis = testEventTimeInMillis,
            ),
            jobAttemptData = JobAttemptData(
                jobAttemptCount = 1,
                lastJobAttemptDateInMillis = testEventTimeInMillis,
                lastJobAttemptActionId = "last82ded-021a-4251-9e03-2c58b130410f",
            ),
            dateCreatedInMillis = 10000000L,
        )
    private val testBroker =
        Broker(
            name = testBrokerName,
            fileName = "test.json",
            url = "testbroker.com",
            version = "1.1.1",
            addedDatetime = 10000000L,
            parent = null,
            removedAt = 0L,
        )

    @Test
    fun whenHandleBrokerManualScanStartedThenSavesLogsAndReportsPixel() =
        runTest {
            val state =
                BrokerScanStarted(
                    broker = testBroker,
                    eventTimeInMillis = testEventTimeInMillis,
                )
            testee.handleState(state)

            verify(mockPixelSender).reportScanStarted(
                brokerUrl = testBroker.url,
            )
            verify(mockEventsRepository).saveBrokerScanLog(
                PirBrokerScanLog(
                    eventTimeInMillis = testEventTimeInMillis,
                    brokerName = testBrokerName,
                    eventType = BROKER_STARTED,
                ),
            )
            verifyNoMoreInteractions(mockEventsRepository)
            verifyNoMoreInteractions(mockPixelSender)
        }

    @Test
    fun whenHandleBrokerManualScanCompletedWithSuccessWithNoMatchThenSavesLogsAndReportsPixel() =
        runTest {
            val state =
                BrokerScanSuccess(
                    broker = testBroker,
                    profileQueryId = testProfileQueryId,
                    startTimeInMillis = testStartTimeInMillis,
                    eventTimeInMillis = testEventTimeInMillis,
                    totalTimeMillis = testTotalTimeMillis,
                    isManualRun = true,
                    lastAction = BrokerAction.Navigate(id = "1234", url = "hello.com"),
                )
            whenever(mockRepository.getExtractedProfiles(any(), any())).thenReturn(emptyList())
            whenever(networkProtectionState.isRunning()).thenReturn(true)
            testee.handleState(state)

            verify(mockPixelSender).reportScanNoMatch(
                brokerUrl = testBroker.url,
                brokerVersion = testBroker.version,
                durationMs = testTotalTimeMillis,
                inManualStarted = true,
                parentUrl = "",
                actionId = "1234",
                actionType = "navigate",
                isVpnRunning = true,
            )
            verify(mockEventsRepository).saveBrokerScanLog(
                PirBrokerScanLog(
                    eventTimeInMillis = testEventTimeInMillis,
                    brokerName = testBrokerName,
                    eventType = BROKER_SUCCESS,
                ),
            )
            verify(mockEventsRepository).saveScanCompletedBroker(
                brokerName = testBrokerName,
                profileQueryId = testProfileQueryId,
                startTimeInMillis = testStartTimeInMillis,
                endTimeInMillis = testEventTimeInMillis,
                isSuccess = true,
            )
            verifyNoInteractions(mockJobRecordUpdater)
        }

    @Test
    fun whenHandleBrokerManualScanCompletedWithSuccessWithMatchesThenSavesLogsAndReportsPixel() =
        runTest {
            val state =
                BrokerScanSuccess(
                    broker = testBroker,
                    profileQueryId = testProfileQueryId,
                    startTimeInMillis = testStartTimeInMillis,
                    eventTimeInMillis = testEventTimeInMillis,
                    totalTimeMillis = testTotalTimeMillis,
                    isManualRun = true,
                    lastAction = BrokerAction.Navigate(id = "1234", url = "hello.com"),
                )
            whenever(mockRepository.getExtractedProfiles(any(), any())).thenReturn(
                listOf(testExtractedProfile),
            )
            whenever(networkProtectionState.isRunning()).thenReturn(true)
            testee.handleState(state)

            verify(mockPixelSender).reportScanMatches(
                brokerUrl = testBroker.url,
                durationMs = testTotalTimeMillis,
                inManualStarted = true,
                parentUrl = "",
                totalMatches = 1,
                isVpnRunning = true,
            )
            verify(mockEventsRepository).saveBrokerScanLog(
                PirBrokerScanLog(
                    eventTimeInMillis = testEventTimeInMillis,
                    brokerName = testBrokerName,
                    eventType = BROKER_SUCCESS,
                ),
            )
            verify(mockEventsRepository).saveScanCompletedBroker(
                brokerName = testBrokerName,
                profileQueryId = testProfileQueryId,
                startTimeInMillis = testStartTimeInMillis,
                endTimeInMillis = testEventTimeInMillis,
                isSuccess = true,
            )
            verifyNoInteractions(mockJobRecordUpdater)
        }

    @Test
    fun whenHandleBrokerManualScanCompletedWithFailureThenSavesErrorLogsAndReportsPixel() =
        runTest {
            val state =
                BrokerScanFailed(
                    broker = testBroker,
                    profileQueryId = testProfileQueryId,
                    startTimeInMillis = testStartTimeInMillis,
                    eventTimeInMillis = testEventTimeInMillis,
                    totalTimeMillis = testTotalTimeMillis,
                    isManualRun = true,
                    errorCategory = "network-error",
                    errorDetails = "Error details",
                    failedAction = BrokerAction.Navigate(id = "123243", url = "test.com"),
                )

            whenever(networkProtectionState.isRunning()).thenReturn(false)

            testee.handleState(state)

            verify(mockPixelSender).reportScanError(
                brokerUrl = testBroker.url,
                brokerVersion = testBroker.version,
                durationMs = testTotalTimeMillis,
                errorCategory = "network-error",
                errorDetails = "Error details",
                inManualStarted = true,
                parentUrl = "",
                actionId = "123243",
                actionType = "navigate",
                isVpnRunning = false,
            )
            verify(mockEventsRepository).saveBrokerScanLog(
                PirBrokerScanLog(
                    eventTimeInMillis = testEventTimeInMillis,
                    brokerName = testBrokerName,
                    eventType = BROKER_ERROR,
                ),
            )
            verify(mockEventsRepository).saveScanCompletedBroker(
                brokerName = testBrokerName,
                profileQueryId = testProfileQueryId,
                startTimeInMillis = testStartTimeInMillis,
                endTimeInMillis = testEventTimeInMillis,
                isSuccess = false,
            )
            verify(mockJobRecordUpdater).updateScanError(testBrokerName, testProfileQueryId)
        }

    @Test
    fun whenHandleBrokerScanActionSucceededWithExtractedResponseAndProfilesFoundThenSavesProfilesAndUpdatesJobs() =
        runTest {
            val extractedResponse =
                ExtractedResponse(
                    actionID = "extract123",
                    actionType = "extract",
                    response = listOf(testScriptExtractedProfile),
                )
            val state =
                BrokerScanActionSucceeded(
                    broker = testBroker,
                    profileQueryId = testProfileQueryId,
                    pirSuccessResponse = extractedResponse,
                )
            val expectedExtractedProfile =
                ExtractedProfile(
                    profileUrl = "https://example.com/profile/123",
                    profileQueryId = testProfileQueryId,
                    brokerName = testBrokerName,
                    name = "John Doe",
                    alternativeNames = listOf("Johnny", "J. Doe"),
                    age = "30",
                    addresses =
                    listOf(
                        AddressCityState(
                            city = "New York",
                            state = "NY",
                            fullAddress = "123 Main St",
                        ),
                    ),
                    phoneNumbers = listOf("555-1234"),
                    relatives = listOf("Jane Doe"),
                    identifier = "id123",
                    reportId = "report123",
                    email = "john@example.com",
                    fullName = "John Michael Doe",
                )

            testee.handleState(state)

            // Verify the order of method calls is correct:
            // 1. Mark removed profiles first - compares new profiles with existing ones to mark any no longer present as removed
            // 2. Save extracted profiles second - persists the new profiles to database
            // 3. Update scan status last - updates job record to indicate matches were found
            // This order ensures data consistency and proper state management
            val inOrder = inOrder(mockJobRecordUpdater, mockRepository)
            inOrder.verify(mockJobRecordUpdater).markRemovedOptOutJobRecords(
                newExtractedProfiles = listOf(expectedExtractedProfile),
                brokerName = testBrokerName,
                profileQueryId = testProfileQueryId,
            )
            inOrder.verify(mockJobRecordUpdater).updateScanMatchesFound(
                listOf(expectedExtractedProfile),
                testBrokerName,
                testProfileQueryId,
            )
            inOrder.verify(mockRepository).saveNewExtractedProfiles(listOf(expectedExtractedProfile))
        }

    @Test
    fun whenHandleBrokerScanActionSucceededWithExtractedResponseAndNoProfilesFoundThenUpdatesJobAsNoMatch() =
        runTest {
            val extractedResponse =
                ExtractedResponse(
                    actionID = "extract123",
                    actionType = "extract",
                    response = emptyList(),
                )
            val state =
                BrokerScanActionSucceeded(
                    broker = testBroker,
                    profileQueryId = testProfileQueryId,
                    pirSuccessResponse = extractedResponse,
                )

            testee.handleState(state)

            verify(mockJobRecordUpdater).updateScanNoMatchFound(testBrokerName, testProfileQueryId)
            verifyNoInteractions(mockRepository)
        }

    @Test
    fun whenHandleBrokerScanActionSucceededWithNonExtractedResponseThenDoesNothing() =
        runTest {
            val navigateResponse =
                NavigateResponse(
                    actionID = "navigate123",
                    actionType = "navigate",
                    response = NavigateResponse.ResponseData(url = "https://example.com"),
                )
            val state =
                BrokerScanActionSucceeded(
                    broker = testBroker,
                    profileQueryId = testProfileQueryId,
                    pirSuccessResponse = navigateResponse,
                )

            testee.handleState(state)

            verifyNoInteractions(mockRepository)
            verifyNoInteractions(mockJobRecordUpdater)
        }

    @Test
    fun whenHandleBrokerRecordOptOutStartedThenMarksOptOutAsAttemptedAndReportsPixel() =
        runTest {
            val state =
                BrokerRecordOptOutStarted(
                    broker = testBroker,
                    extractedProfile = testExtractedProfile,
                    attemptId = "c9982ded-021a-4251-9e03-2c58b130410f",
                )

            testee.handleState(state)

            verify(mockJobRecordUpdater).markOptOutAsAttempted(testExtractedProfileId)
            verify(mockPixelSender).reportOptOutStageStart(
                brokerUrl = testBroker.url,
                parentUrl = testBroker.parent.orEmpty(),
            )
            verifyNoMoreInteractions(mockPixelSender)
        }

    @Test
    fun whenHandleBrokerRecordOptOutCompletedWithSuccessThenUpdatesRecordAndReportsPixel() =
        runTest {
            val state =
                BrokerRecordOptOutSubmitted(
                    broker = testBroker,
                    extractedProfile = testExtractedProfile,
                    startTimeInMillis = testStartTimeInMillis,
                    endTimeInMillis = testEventTimeInMillis,
                    attemptId = "c9982ded-021a-4251-9e03-2c58b130410f",
                    emailPattern = "ep15",
                )
            whenever(mockRepository.getBrokerForName(testBrokerName)).thenReturn(testBroker)
            whenever(mockJobRecordUpdater.updateOptOutRequested(any())).thenReturn(
                OptOutJobRecord(
                    extractedProfileId = 789L,
                    brokerName = testBrokerName,
                    userProfileId = 123L,
                    status = OptOutJobStatus.REQUESTED,
                    attemptCount = 2,
                    lastOptOutAttemptDateInMillis = 1000L,
                    optOutRequestedDateInMillis = 2000L,
                    optOutRemovedDateInMillis = 0L,
                ),
            )
            whenever(networkProtectionState.isRunning()).thenReturn(false)

            testee.handleState(state)

            verify(mockJobRecordUpdater).updateOptOutRequested(testExtractedProfileId)
            verify(mockEventsRepository).saveOptOutCompleted(
                brokerName = testBrokerName,
                extractedProfile = testExtractedProfile,
                startTimeInMillis = testStartTimeInMillis,
                endTimeInMillis = testEventTimeInMillis,
                isSubmitSuccess = true,
            )
            verify(mockPixelSender).reportOptOutSubmitted(
                brokerUrl = testBroker.url,
                parent = "",
                durationMs = testEventTimeInMillis - testStartTimeInMillis,
                optOutAttemptCount = 2,
                emailPattern = state.emailPattern,
                isVpnRunning = false,
            )
        }

    @Test
    fun whenHandleBrokerRecordOptOutCompletedWithFailureThenUpdatesRecordAndReportsPixel() =
        runTest {
            val state =
                BrokerRecordOptOutFailed(
                    broker = testBroker,
                    extractedProfile = testExtractedProfile,
                    startTimeInMillis = testStartTimeInMillis,
                    endTimeInMillis = testEventTimeInMillis,
                    attemptId = "c9982ded-021a-4251-9e03-2c58b130410f",
                    failedAction = BrokerAction.Navigate(
                        id = "fail82ded-021a-4251-9e03-2c58b130410f",
                        url = "https://example.com/fail",
                    ),
                    stage = OTHER,
                    emailPattern = "ep15",
                )
            whenever(mockRepository.getBrokerForName(testBrokerName)).thenReturn(testBroker)
            whenever(mockJobRecordUpdater.updateOptOutError(any())).thenReturn(
                OptOutJobRecord(
                    extractedProfileId = 789L,
                    brokerName = testBrokerName,
                    userProfileId = 123L,
                    status = OptOutJobStatus.ERROR,
                    attemptCount = 2,
                    lastOptOutAttemptDateInMillis = 1000L,
                    optOutRequestedDateInMillis = 2000L,
                    optOutRemovedDateInMillis = 0L,
                ),
            )
            whenever(networkProtectionState.isRunning()).thenReturn(true)

            testee.handleState(state)

            verify(mockJobRecordUpdater).updateOptOutError(testExtractedProfileId)
            verify(mockEventsRepository).saveOptOutCompleted(
                brokerName = testBrokerName,
                extractedProfile = testExtractedProfile,
                startTimeInMillis = testStartTimeInMillis,
                endTimeInMillis = testEventTimeInMillis,
                isSubmitSuccess = false,
            )
            verify(mockPixelSender).reportOptOutFailed(
                brokerUrl = testBroker.url,
                parent = "",
                brokerJsonVersion = testBroker.version,
                durationMs = testEventTimeInMillis - testStartTimeInMillis,
                stage = state.stage,
                tries = 2,
                emailPattern = state.emailPattern,
                actionId = state.failedAction.id,
                actionType = state.failedAction.asActionType(),
                isVpnRunning = true,
            )
        }

    @Test
    fun whenHandleBrokerRecordEmailConfirmationNeededThenSavesEmailConfirmationJobAndMarksOptOutAsWaitingForEmailConfirmation() =
        runTest {
            val state =
                BrokerRecordEmailConfirmationNeeded(
                    broker = testBroker,
                    extractedProfile = testExtractedProfile,
                    attemptId = "c9982ded-021a-4251-9e03-2c58b130410f",
                    lastActionId = "hello82ded-021a-4251-9e03-2c58b130410f",
                    durationMs = testTotalTimeMillis,
                    currentActionAttemptCount = 1,
                )
            whenever(mockRepository.getBrokerForName(testBrokerName)).thenReturn(testBroker)

            testee.handleState(state)

            verify(mockJobRecordUpdater).markOptOutAsWaitingForEmailConfirmation(
                profileQueryId = testProfileQueryId,
                extractedProfileId = testExtractedProfileId,
                brokerName = testBrokerName,
                email = "john@example.com",
                attemptId = "c9982ded-021a-4251-9e03-2c58b130410f",
            )
            verify(mockPixelSender).reportStagePendingEmailConfirmation(
                brokerUrl = testBroker.url,
                brokerVersion = testBroker.version,
                actionId = "hello82ded-021a-4251-9e03-2c58b130410f",
                durationMs = testTotalTimeMillis,
                tries = 1,
            )
        }

    @Test
    fun whenHandleBrokerRecordEmailConfirmationStartedThenUpdateEmailJobRecordAndEmitPixel() =
        runTest {
            val state =
                BrokerRecordEmailConfirmationStarted(
                    broker = testBroker,
                    extractedProfileId = testExtractedProfileId,
                    firstActionId = "first82ded-021a-4251-9e03-2c58b130410f",
                )
            whenever(mockRepository.getBrokerForName(testBrokerName)).thenReturn(testBroker)
            whenever(mockJobRecordUpdater.recordEmailConfirmationAttempt(any())).thenReturn(testEmailConfirmationJob)

            testee.handleState(state)

            verify(mockJobRecordUpdater).recordEmailConfirmationAttempt(
                extractedProfileId = testExtractedProfileId,
            )

            verify(mockPixelSender).reportEmailConfirmationAttemptStart(
                brokerUrl = testBroker.url,
                brokerVersion = testBroker.version,
                actionId = state.firstActionId,
                attemptNumber = testEmailConfirmationJob.jobAttemptData.jobAttemptCount,
            )
            verifyNoMoreInteractions(mockPixelSender)
            verifyNoMoreInteractions(mockJobRecordUpdater)
        }

    @Test
    fun whenHandleBrokerRecordEmailConfirmationCompletedSuccessThenUpdateEmailJobRecordAndEmitPixel() =
        runTest {
            val state =
                BrokerRecordEmailConfirmationCompleted(
                    broker = testBroker,
                    extractedProfile = testExtractedProfile,
                    isSuccess = true,
                    lastActionId = "last82ded-021a-4251-9e03-2c58b130410f",
                    totalTimeMillis = 1000L,
                    emailPattern = "",
                    attemptId = "attemptid-123",
                )
            whenever(mockRepository.getBrokerForName(testBrokerName)).thenReturn(testBroker)
            whenever(mockSchedulingRepository.getEmailConfirmationJob(testExtractedProfileId)).thenReturn(testEmailConfirmationJob)
            whenever(networkProtectionState.isRunning()).thenThrow(RuntimeException())

            testee.handleState(state)

            verify(mockPixelSender).reportEmailConfirmationAttemptSuccess(
                brokerUrl = testBroker.url,
                brokerVersion = testBroker.version,
                actionId = state.lastActionId,
                attemptNumber = testEmailConfirmationJob.jobAttemptData.jobAttemptCount,
                durationMs = state.totalTimeMillis,
            )
            verify(mockJobRecordUpdater).recordEmailConfirmationCompleted(
                extractedProfileId = testExtractedProfileId,
            )
            verify(mockPixelSender).reportEmailConfirmationJobSuccess(
                brokerUrl = testBroker.url,
                brokerVersion = testBroker.version,
            )
            verify(mockEventsRepository).saveEmailConfirmationLog(
                testEventTimeInMillis,
                EMAIL_CONFIRMATION_SUCCESS,
                testBrokerName,
            )
            verify(mockPixelSender).reportOptOutSubmitted(
                brokerUrl = testBroker.url,
                parent = "",
                durationMs = state.totalTimeMillis,
                optOutAttemptCount = testEmailConfirmationJob.jobAttemptData.jobAttemptCount,
                emailPattern = state.emailPattern,
                isVpnRunning = false,
            )
            verifyNoMoreInteractions(mockPixelSender)
            verifyNoMoreInteractions(mockJobRecordUpdater)
        }

    @Test
    fun whenHandleBrokerRecordEmailConfirmationCompletedFailedThenUpdateEmailJobRecordAndEmitPixel() =
        runTest {
            val state =
                BrokerRecordEmailConfirmationCompleted(
                    broker = testBroker,
                    extractedProfile = testExtractedProfile,
                    isSuccess = false,
                    lastActionId = "last82ded-021a-4251-9e03-2c58b130410f",
                    totalTimeMillis = 1000L,
                    attemptId = "attemptid-123",
                    emailPattern = "",
                )
            whenever(mockRepository.getBrokerForName(testBrokerName)).thenReturn(testBroker)
            whenever(mockJobRecordUpdater.recordEmailConfirmationFailed(any(), any())).thenReturn(testEmailConfirmationJob)

            testee.handleState(state)

            verify(mockJobRecordUpdater).recordEmailConfirmationFailed(
                extractedProfileId = testExtractedProfileId,
                lastActionId = state.lastActionId,
            )

            verify(mockPixelSender).reportEmailConfirmationAttemptFailed(
                brokerUrl = testBroker.url,
                brokerVersion = testBroker.version,
                actionId = state.lastActionId,
                attemptNumber = testEmailConfirmationJob.jobAttemptData.jobAttemptCount,
                durationMs = state.totalTimeMillis,
            )
            verify(mockEventsRepository).saveEmailConfirmationLog(
                testEventTimeInMillis,
                EMAIL_CONFIRMATION_FAILED,
                testBrokerName,
            )
            verifyNoMoreInteractions(mockPixelSender)
            verifyNoMoreInteractions(mockJobRecordUpdater)
        }

    @Test
    fun whenHandleBrokerStepInvalidEventWithManualRunTypeThenReportsScanInvalidEvent() =
        runTest {
            val state = BrokerStepInvalidEvent(
                broker = testBroker,
                runType = RunType.MANUAL,
            )

            testee.handleState(state)

            verify(mockPixelSender).reportScanInvalidEvent(
                brokerUrl = testBroker.url,
                brokerVersion = testBroker.version,
            )
            verifyNoMoreInteractions(mockPixelSender)
        }

    @Test
    fun whenHandleBrokerStepInvalidEventWithScheduledRunTypeThenReportsScanInvalidEvent() =
        runTest {
            val state = BrokerStepInvalidEvent(
                broker = testBroker,
                runType = RunType.SCHEDULED,
            )

            testee.handleState(state)

            verify(mockPixelSender).reportScanInvalidEvent(
                brokerUrl = testBroker.url,
                brokerVersion = testBroker.version,
            )
            verifyNoMoreInteractions(mockPixelSender)
        }

    @Test
    fun whenHandleBrokerStepInvalidEventWithOptOutRunTypeThenReportsOptOutInvalidEvent() =
        runTest {
            val state = BrokerStepInvalidEvent(
                broker = testBroker,
                runType = RunType.OPTOUT,
            )

            testee.handleState(state)

            verify(mockPixelSender).reportOptOutInvalidEvent(
                brokerUrl = testBroker.url,
                brokerVersion = testBroker.version,
            )
            verifyNoMoreInteractions(mockPixelSender)
        }

    @Test
    fun whenHandleBrokerStepInvalidEventWithEmailConfirmationRunTypeThenReportsOptOutInvalidEvent() =
        runTest {
            val state = BrokerStepInvalidEvent(
                broker = testBroker,
                runType = RunType.EMAIL_CONFIRMATION,
            )

            testee.handleState(state)

            verify(mockPixelSender).reportOptOutInvalidEvent(
                brokerUrl = testBroker.url,
                brokerVersion = testBroker.version,
            )
            verifyNoMoreInteractions(mockPixelSender)
        }
}
