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

package com.duckduckgo.pir.impl.common.actions

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.EmailConfirmationStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.OptOutStepActions
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageValidate
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordEmailConfirmationCompleted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordEmailConfirmationNeeded
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutFailed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutSubmitted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanFailed
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanSuccess
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted.StepStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerStep
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.EmailData
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.JobAttemptData
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.LinkFetchData
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.duckduckgo.pir.impl.store.PirRepository.GeneratedEmailData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BrokerStepCompletedEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: BrokerStepCompletedEventHandler
    private val mockPirRunStateHandler: PirRunStateHandler = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()

    private val testProfileQueryId = 123L
    private val testBrokerName = "test-broker"
    private val testCurrentTimeInMillis = 10000L
    private val testBrokerStartTime = 5000L
    private val testStageStartMs = 8000L

    private val testProfileQuery =
        ProfileQuery(
            id = testProfileQueryId,
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

    private val testExtractedProfile =
        ExtractedProfile(
            profileQueryId = testProfileQueryId,
            brokerName = testBrokerName,
            name = "John Doe",
        )

    private val testBroker = Broker(
        name = testBrokerName,
        fileName = "test-broker.json",
        url = "https://test-broker.com",
        version = "1.0",
        parent = null,
        addedDatetime = 124354,
        removedAt = 0L,
    )

    private val testAction1 = BrokerAction.Navigate(
        id = "action-1",
        url = "https://example.com",
    )

    private val testAction2 = BrokerAction.Click(
        id = "action-2",
        elements = emptyList(),
        selector = null,
    )

    private val testAction3 = BrokerAction.FillForm(
        id = "action-3",
        elements = emptyList(),
        selector = "form",
    )

    private val testEmailConfirmationJob =
        EmailConfirmationJobRecord(
            brokerName = testBrokerName,
            userProfileId = testProfileQueryId,
            extractedProfileId = 456L,
            emailData = EmailData(
                email = "john@example.com",
                attemptId = "test-attempt-id",
            ),
            linkFetchData = LinkFetchData(
                emailConfirmationLink = "https://example.com/confirm",
                linkFetchAttemptCount = 0,
                lastLinkFetchDateInMillis = 0L,
            ),
            jobAttemptData = JobAttemptData(
                jobAttemptCount = 0,
                lastJobAttemptDateInMillis = 0L,
                lastJobAttemptActionId = "",
            ),
            dateCreatedInMillis = 10000000L,
        )

    private val testGeneratedEmailData = GeneratedEmailData(
        emailAddress = "generated@example.com",
        pattern = "pattern-123",
    )

    @Before
    fun setUp() {
        testee = BrokerStepCompletedEventHandler(
            mockPirRunStateHandler,
            mockCurrentTimeProvider,
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTimeInMillis)
    }

    @Test
    fun whenEventIsBrokerStepCompletedThenEventTypeIsCorrect() {
        assertEquals(BrokerStepCompleted::class, testee.event)
    }

    @Test
    fun whenNeedsEmailConfirmationTrueThenEmitsEmailConfirmationNeededState() = runTest {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testAction1, testAction2, testAction3),
                optOutType = "form",
            ),
            profileToOptOut = testExtractedProfile,
        )
        val state = State(
            runType = RunType.OPTOUT,
            brokerStepsToExecute = listOf(optOutStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 2,
            actionRetryCount = 1,
            attemptId = "attempt-123",
            brokerStepStartTime = testBrokerStartTime,
            stageStatus = PirStageStatus(
                currentStage = PirStage.FILL_FORM,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = BrokerStepCompleted(
            needsEmailConfirmation = true,
            stepStatus = StepStatus.Success,
        )

        testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerRecordEmailConfirmationNeeded>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(testBroker, capturedState.firstValue.broker)
        assertEquals(testExtractedProfile, capturedState.firstValue.extractedProfile)
        assertEquals("attempt-123", capturedState.firstValue.attemptId)
        assertEquals("action-3", capturedState.firstValue.lastActionId)
        assertEquals(testCurrentTimeInMillis - testStageStartMs, capturedState.firstValue.durationMs)
        assertEquals(2, capturedState.firstValue.currentActionAttemptCount)
    }

    @Test
    fun whenNeedsEmailConfirmationTrueThenIncrementsStepIndexAndReturnsNextEvent() = runTest {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testAction1),
                optOutType = "form",
            ),
            profileToOptOut = testExtractedProfile,
        )
        val state = State(
            runType = RunType.OPTOUT,
            brokerStepsToExecute = listOf(optOutStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            actionRetryCount = 3,
            generatedEmailData = testGeneratedEmailData,
            stageStatus = PirStageStatus(
                currentStage = PirStage.FILL_FORM,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = BrokerStepCompleted(
            needsEmailConfirmation = true,
            stepStatus = StepStatus.Success,
        )

        val result = testee.invoke(state, event)

        assertEquals(1, result.nextState.currentBrokerStepIndex)
        assertEquals(0, result.nextState.actionRetryCount)
        assertNull(result.nextState.generatedEmailData)
        assertEquals(PirStage.VALIDATE, result.nextState.stageStatus.currentStage)
        assertEquals(testCurrentTimeInMillis, result.nextState.stageStatus.stageStartMs)
        assertEquals(ExecuteNextBrokerStep, result.nextEvent)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenManualScanSucceedsThenEmitsBrokerScanSuccessState() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction1, testAction2),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 2,
            brokerStepStartTime = testBrokerStartTime,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = BrokerStepCompleted(
            needsEmailConfirmation = false,
            stepStatus = StepStatus.Success,
        )

        testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerScanSuccess>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(testBroker, capturedState.firstValue.broker)
        assertEquals(testProfileQueryId, capturedState.firstValue.profileQueryId)
        assertEquals(testCurrentTimeInMillis, capturedState.firstValue.eventTimeInMillis)
        assertEquals(testCurrentTimeInMillis - testBrokerStartTime, capturedState.firstValue.totalTimeMillis)
        assertEquals(testBrokerStartTime, capturedState.firstValue.startTimeInMillis)
        assertEquals(true, capturedState.firstValue.isManualRun)
        assertEquals(testAction2, capturedState.firstValue.lastAction)
    }

    @Test
    fun whenScheduledScanSucceedsThenEmitsBrokerScanSuccessStateWithManualFalse() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction1, testAction2, testAction3),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.SCHEDULED,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 3,
            brokerStepStartTime = testBrokerStartTime,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = BrokerStepCompleted(
            needsEmailConfirmation = false,
            stepStatus = StepStatus.Success,
        )

        testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerScanSuccess>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(false, capturedState.firstValue.isManualRun)
        assertEquals(testAction3, capturedState.firstValue.lastAction)
    }

    @Test
    fun whenManualScanFailsThenEmitsBrokerScanFailedState() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction1, testAction2),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 1,
            brokerStepStartTime = testBrokerStartTime,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = testStageStartMs,
            ),
        )
        val testError = PirError.JsError.ActionError("Test error")
        val event = BrokerStepCompleted(
            needsEmailConfirmation = false,
            stepStatus = StepStatus.Failure(error = testError),
        )

        testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerScanFailed>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(testBroker, capturedState.firstValue.broker)
        assertEquals(testProfileQueryId, capturedState.firstValue.profileQueryId)
        assertEquals(testCurrentTimeInMillis, capturedState.firstValue.eventTimeInMillis)
        assertEquals(testCurrentTimeInMillis - testBrokerStartTime, capturedState.firstValue.totalTimeMillis)
        assertEquals(testBrokerStartTime, capturedState.firstValue.startTimeInMillis)
        assertEquals(true, capturedState.firstValue.isManualRun)
        assertEquals("validation-error", capturedState.firstValue.errorCategory)
        assertEquals("Test error", capturedState.firstValue.errorDetails)
        assertEquals(testAction2, capturedState.firstValue.failedAction)
    }

    @Test
    fun whenScheduledScanFailsThenEmitsBrokerScanFailedStateWithManualFalse() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction1),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.SCHEDULED,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            brokerStepStartTime = testBrokerStartTime,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = testStageStartMs,
            ),
        )
        val testError = PirError.UnableToLoadBrokerUrl
        val event = BrokerStepCompleted(
            needsEmailConfirmation = false,
            stepStatus = StepStatus.Failure(error = testError),
        )

        testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerScanFailed>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(false, capturedState.firstValue.isManualRun)
        assertEquals("network-error", capturedState.firstValue.errorCategory)
        assertEquals("Unable to load broker url", capturedState.firstValue.errorDetails)
        assertEquals(testAction1, capturedState.firstValue.failedAction)
    }

    @Test
    fun whenOptOutSucceedsThenEmitsTwoStates() = runTest {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testAction1, testAction2),
                optOutType = "form",
            ),
            profileToOptOut = testExtractedProfile,
        )
        val state = State(
            runType = RunType.OPTOUT,
            brokerStepsToExecute = listOf(optOutStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 2,
            actionRetryCount = 0,
            attemptId = "attempt-456",
            brokerStepStartTime = testBrokerStartTime,
            generatedEmailData = testGeneratedEmailData,
            stageStatus = PirStageStatus(
                currentStage = PirStage.FILL_FORM,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = BrokerStepCompleted(
            needsEmailConfirmation = false,
            stepStatus = StepStatus.Success,
        )

        testee.invoke(state, event)

        verify(mockPirRunStateHandler, times(2)).handleState(any())

        val capturedValidateState = argumentCaptor<BrokerOptOutStageValidate>()
        val capturedSubmittedState = argumentCaptor<BrokerRecordOptOutSubmitted>()
        verify(mockPirRunStateHandler).handleState(capturedValidateState.capture())
        verify(mockPirRunStateHandler).handleState(capturedSubmittedState.capture())

        // Validate state
        assertEquals(testBroker, capturedValidateState.firstValue.broker)
        assertEquals("action-2", capturedValidateState.firstValue.actionID)
        assertEquals("attempt-456", capturedValidateState.firstValue.attemptId)
        assertEquals(testCurrentTimeInMillis - testStageStartMs, capturedValidateState.firstValue.durationMs)
        assertEquals(1, capturedValidateState.firstValue.currentActionAttemptCount)

        // Submitted state
        assertEquals(testBroker, capturedSubmittedState.firstValue.broker)
        assertEquals(testExtractedProfile, capturedSubmittedState.firstValue.extractedProfile)
        assertEquals("attempt-456", capturedSubmittedState.firstValue.attemptId)
        assertEquals(testBrokerStartTime, capturedSubmittedState.firstValue.startTimeInMillis)
        assertEquals(testCurrentTimeInMillis, capturedSubmittedState.firstValue.endTimeInMillis)
        assertEquals("pattern-123", capturedSubmittedState.firstValue.emailPattern)
    }

    @Test
    fun whenOptOutSucceedsWithoutEmailDataThenEmailPatternIsNull() = runTest {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testAction1),
                optOutType = "form",
            ),
            profileToOptOut = testExtractedProfile,
        )
        val state = State(
            runType = RunType.OPTOUT,
            brokerStepsToExecute = listOf(optOutStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 1,
            attemptId = "attempt-789",
            brokerStepStartTime = testBrokerStartTime,
            generatedEmailData = null,
            stageStatus = PirStageStatus(
                currentStage = PirStage.FILL_FORM,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = BrokerStepCompleted(
            needsEmailConfirmation = false,
            stepStatus = StepStatus.Success,
        )

        testee.invoke(state, event)

        val capturedSubmittedState = argumentCaptor<BrokerRecordOptOutSubmitted>()
        verify(mockPirRunStateHandler).handleState(capturedSubmittedState.capture())
        assertNull(capturedSubmittedState.firstValue.emailPattern)
    }

    @Test
    fun whenOptOutFailsThenEmitsOptOutFailedState() = runTest {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testAction1, testAction2, testAction3),
                optOutType = "form",
            ),
            profileToOptOut = testExtractedProfile,
        )
        val state = State(
            runType = RunType.OPTOUT,
            brokerStepsToExecute = listOf(optOutStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 1,
            attemptId = "attempt-999",
            brokerStepStartTime = testBrokerStartTime,
            generatedEmailData = testGeneratedEmailData,
            stageStatus = PirStageStatus(
                currentStage = PirStage.CAPTCHA_SOLVE,
                stageStartMs = testStageStartMs,
            ),
        )
        val testError = PirError.JsError.ActionError("Captcha failed")
        val event = BrokerStepCompleted(
            needsEmailConfirmation = false,
            stepStatus = StepStatus.Failure(error = testError),
        )

        testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerRecordOptOutFailed>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(testBroker, capturedState.firstValue.broker)
        assertEquals(testExtractedProfile, capturedState.firstValue.extractedProfile)
        assertEquals(testBrokerStartTime, capturedState.firstValue.startTimeInMillis)
        assertEquals(testCurrentTimeInMillis, capturedState.firstValue.endTimeInMillis)
        assertEquals("attempt-999", capturedState.firstValue.attemptId)
        assertEquals(testAction2, capturedState.firstValue.failedAction)
        assertEquals(PirStage.CAPTCHA_SOLVE, capturedState.firstValue.stage)
        assertEquals("pattern-123", capturedState.firstValue.emailPattern)
    }

    @Test
    fun whenEmailConfirmationSucceedsThenEmitsCompletedState() = runTest {
        val emailConfirmationStep = EmailConfirmationStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testAction1, testAction2),
                optOutType = "form",
            ),
            emailConfirmationJob = testEmailConfirmationJob,
            profileToOptOut = testExtractedProfile,
        )
        val state = State(
            runType = RunType.EMAIL_CONFIRMATION,
            brokerStepsToExecute = listOf(emailConfirmationStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 2,
            brokerStepStartTime = testBrokerStartTime,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = testStageStartMs,
            ),
            attemptId = "attempt-123",
        )
        val event = BrokerStepCompleted(
            needsEmailConfirmation = false,
            stepStatus = StepStatus.Success,
        )

        testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerRecordEmailConfirmationCompleted>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(testBroker, capturedState.firstValue.broker)
        assertEquals(true, capturedState.firstValue.isSuccess)
        assertEquals("action-2", capturedState.firstValue.lastActionId)
        assertEquals(testCurrentTimeInMillis - testBrokerStartTime, capturedState.firstValue.totalTimeMillis)
        assertEquals(testExtractedProfile, capturedState.firstValue.extractedProfile)
        assertEquals("", capturedState.firstValue.emailPattern)
        assertEquals("attempt-123", capturedState.firstValue.attemptId)
    }

    @Test
    fun whenEmailConfirmationFailsThenEmitsCompletedStateWithFailure() = runTest {
        val emailConfirmationStep = EmailConfirmationStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testAction1),
                optOutType = "form",
            ),
            emailConfirmationJob = testEmailConfirmationJob,
            profileToOptOut = testExtractedProfile,
        )
        val state = State(
            runType = RunType.EMAIL_CONFIRMATION,
            brokerStepsToExecute = listOf(emailConfirmationStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            brokerStepStartTime = testBrokerStartTime,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = testStageStartMs,
            ),
        )
        val testError = PirError.JsError.ActionError("Email confirmation error")
        val event = BrokerStepCompleted(
            needsEmailConfirmation = false,
            stepStatus = StepStatus.Failure(error = testError),
        )

        testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerRecordEmailConfirmationCompleted>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(false, capturedState.firstValue.isSuccess)
        assertEquals("action-1", capturedState.firstValue.lastActionId)
    }

    @Test
    fun whenBrokerStepCompletedThenStateIsUpdatedCorrectly() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction1),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 1,
            actionRetryCount = 5,
            generatedEmailData = testGeneratedEmailData,
            transactionID = "txn-123",
            brokerStepStartTime = testBrokerStartTime,
            stageStatus = PirStageStatus(
                currentStage = PirStage.FILL_FORM,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = BrokerStepCompleted(
            needsEmailConfirmation = false,
            stepStatus = StepStatus.Success,
        )

        val result = testee.invoke(state, event)

        assertEquals(1, result.nextState.currentBrokerStepIndex)
        assertEquals(0, result.nextState.actionRetryCount)
        assertNull(result.nextState.generatedEmailData)
        assertEquals(PirStage.VALIDATE, result.nextState.stageStatus.currentStage)
        assertEquals(testCurrentTimeInMillis, result.nextState.stageStatus.stageStartMs)
        assertEquals("txn-123", result.nextState.transactionID)
        assertEquals(testBrokerStartTime, result.nextState.brokerStepStartTime)
    }

    @Test
    fun whenBrokerStepCompletedThenReturnsExecuteNextBrokerStepEvent() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction1),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 1,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = BrokerStepCompleted(
            needsEmailConfirmation = false,
            stepStatus = StepStatus.Success,
        )

        val result = testee.invoke(state, event)

        assertEquals(ExecuteNextBrokerStep, result.nextEvent)
        assertNull(result.sideEffect)
    }
}
