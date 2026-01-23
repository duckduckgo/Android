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
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerStepInvalidEvent
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerActionFailed
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ErrorReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.CaptchaServiceError
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.EmailError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class ErrorReceivedHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: ErrorReceivedHandler
    private var mockPirRunStateHandler: PirRunStateHandler = mock()

    private val testBroker = Broker(
        name = "test-broker",
        fileName = "test-broker.json",
        url = "https://test-broker.com",
        version = "1.0",
        parent = null,
        addedDatetime = 124354,
        removedAt = 0L,
    )

    private val testAction = BrokerAction.Navigate(
        id = "action-1",
        url = "https://example.com",
    )

    private val testProfileQuery =
        ProfileQuery(
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

    @Before
    fun setUp() {
        testee = ErrorReceivedHandler(mockPirRunStateHandler)
    }

    @Test
    fun whenEventIsErrorReceivedThenEventTypeIsCorrect() {
        assertEquals(ErrorReceived::class, testee.event)
    }

    @Test
    fun whenErrorReceivedWithJsErrorThenReturnsBrokerActionFailedWithNoRetry() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val testError = PirError.JsError.ActionError("Test JS error")
        val event = ErrorReceived(error = testError)

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        val nextEvent = result.nextEvent as BrokerActionFailed
        assertEquals(testError, nextEvent.error)
        assertFalse(nextEvent.allowRetry)
        assertNull(result.sideEffect)
        verifyNoInteractions(mockPirRunStateHandler)
    }

    @Test
    fun whenErrorReceivedWithCaptchaServiceErrorThenReturnsBrokerActionFailedWithNoRetry() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            actionRetryCount = 2,
            stageStatus = PirStageStatus(
                currentStage = PirStage.CAPTCHA_SEND,
                stageStartMs = 1000L,
            ),
        )
        val testError = CaptchaServiceError(
            actionID = testAction.id,
            errorCode = 500,
            errorDetails = "Service unavailable",
        )
        val event = ErrorReceived(error = testError)

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        val nextEvent = result.nextEvent as BrokerActionFailed
        assertEquals(testError, nextEvent.error)
        assertFalse(nextEvent.allowRetry)
        verifyNoInteractions(mockPirRunStateHandler)
    }

    @Test
    fun whenErrorReceivedWithEmailErrorThenReturnsBrokerActionFailedWithNoRetry() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.OPTOUT,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.EMAIL_GENERATE,
                stageStartMs = 2000L,
            ),
        )
        val testError = EmailError(
            actionID = testAction.id,
            errorCode = 404,
            error = "Email service error",
        )
        val event = ErrorReceived(error = testError)

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        val nextEvent = result.nextEvent as BrokerActionFailed
        assertEquals(testError, nextEvent.error)
        assertFalse(nextEvent.allowRetry)
        verifyNoInteractions(mockPirRunStateHandler)
    }

    @Test
    fun whenErrorReceivedWithUnknownErrorThenReturnsBrokerActionFailedWithNoRetry() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.SCHEDULED,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val testError = PirError.Unknown("Unknown error occurred")
        val event = ErrorReceived(error = testError)

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        val nextEvent = result.nextEvent as BrokerActionFailed
        assertEquals(testError, nextEvent.error)
        assertFalse(nextEvent.allowRetry)
        verifyNoInteractions(mockPirRunStateHandler)
    }

    @Test
    fun whenErrorReceivedThenStateRemainsCompletelyUnchanged() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 2,
            currentActionIndex = 5,
            actionRetryCount = 3,
            brokerStepStartTime = 5000L,
            transactionID = "txn-123",
            attemptId = "attempt-456",
            pendingUrl = "https://example.com",
            stageStatus = PirStageStatus(
                currentStage = PirStage.FILL_FORM,
                stageStartMs = 7000L,
            ),
        )
        val testError = PirError.JsError.ActionError("Error")
        val event = ErrorReceived(error = testError)

        val result = testee.invoke(state, event)

        // Verify state is completely unchanged
        assertEquals(state, result.nextState)
        assertEquals(2, result.nextState.currentBrokerStepIndex)
        assertEquals(5, result.nextState.currentActionIndex)
        assertEquals(3, result.nextState.actionRetryCount)
        assertEquals(5000L, result.nextState.brokerStepStartTime)
        assertEquals("txn-123", result.nextState.transactionID)
        assertEquals("attempt-456", result.nextState.attemptId)
        assertEquals("https://example.com", result.nextState.pendingUrl)
        assertEquals(PirStage.FILL_FORM, result.nextState.stageStatus.currentStage)
        assertEquals(7000L, result.nextState.stageStatus.stageStartMs)
    }

    @Test
    fun whenBrokerStepIndexExceedsBrokerStepsSizeThenEventIsInvalidAndReturnsUnchangedState() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 5, // Exceeds broker steps size (1)
            currentActionIndex = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val testError = CaptchaServiceError(
            actionID = testAction.id,
            errorCode = 500,
            errorDetails = "Service unavailable",
        )
        val event = ErrorReceived(error = testError)

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        assertNull(result.nextEvent)
        assertNull(result.sideEffect)
        verify(mockPirRunStateHandler).handleState(
            BrokerStepInvalidEvent(
                broker = Broker.unknown(),
                runType = RunType.MANUAL,
            ),
        )
    }

    @Test
    fun whenActionIndexExceedsActionsSizeThenEventIsInvalidAndReturnsUnchangedState() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 10, // Exceeds actions size (1)
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val testError = CaptchaServiceError(
            actionID = testAction.id,
            errorCode = 500,
            errorDetails = "Service unavailable",
        )
        val event = ErrorReceived(error = testError)

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        assertNull(result.nextEvent)
        assertNull(result.sideEffect)
        verify(mockPirRunStateHandler).handleState(
            BrokerStepInvalidEvent(
                broker = testBroker,
                runType = RunType.MANUAL,
            ),
        )
    }

    @Test
    fun whenActionErrorWithMismatchedActionIdThenEventIsInvalidAndReturnsUnchangedState() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction), // testAction.id = "action-1"
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val testError = CaptchaServiceError(
            actionID = "different-action-id", // Does not match "action-1"
            errorCode = 500,
            errorDetails = "Service unavailable",
        )
        val event = ErrorReceived(error = testError)

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        assertNull(result.nextEvent)
        assertNull(result.sideEffect)
        verify(mockPirRunStateHandler).handleState(
            BrokerStepInvalidEvent(
                broker = testBroker,
                runType = RunType.MANUAL,
            ),
        )
    }

    @Test
    fun whenActionErrorWithMatchingActionIdThenEventIsValidAndReturnsBrokerActionFailed() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction), // testAction.id = "action-1"
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val testError = CaptchaServiceError(
            actionID = testAction.id, // Matches "action-1"
            errorCode = 500,
            errorDetails = "Service unavailable",
        )
        val event = ErrorReceived(error = testError)

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        val nextEvent = result.nextEvent as BrokerActionFailed
        assertEquals(testError, nextEvent.error)
        assertFalse(nextEvent.allowRetry)
        verifyNoInteractions(mockPirRunStateHandler)
    }

    @Test
    fun whenNonActionErrorThenEventIsValidRegardlessOfActionId() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction), // testAction.id = "action-1"
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        // JsError is not an ActionError, so action ID check is skipped
        val testError = PirError.JsError.ActionError("Some JS error")
        val event = ErrorReceived(error = testError)

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        val nextEvent = result.nextEvent as BrokerActionFailed
        assertEquals(testError, nextEvent.error)
        assertFalse(nextEvent.allowRetry)
        verifyNoInteractions(mockPirRunStateHandler)
    }
}
