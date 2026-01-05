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
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.OptOutStepActions
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.actions.BrokerActionFailedEventHandler.Companion.MAX_RETRY_COUNT_OPTOUT
import com.duckduckgo.pir.impl.common.actions.BrokerActionFailedEventHandler.Companion.MAX_RETRY_COUNT_SCAN
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerActionFailed
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted.StepStatus.Failure
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BrokerActionFailedEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: BrokerActionFailedEventHandler
    private val mockPirRunStateHandler: PirRunStateHandler = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()

    private val testProfileQueryId = 123L
    private val testBrokerName = "test-broker"
    private val testCurrentTimeInMillis = 5000L

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

    private val testClickAction = BrokerAction.Click(
        id = "click-action-1",
        elements = emptyList(),
        selector = null,
    )

    private val testExpectationAction = BrokerAction.Expectation(
        id = "expectation-action-1",
        expectations = emptyList(),
    )

    private val testError = PirError.JsError.ActionError("Test error")

    @Before
    fun setUp() {
        testee = BrokerActionFailedEventHandler(
            mockPirRunStateHandler,
            mockCurrentTimeProvider,
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTimeInMillis)
    }

    @Test
    fun whenEventIsBrokerActionFailedThenEventTypeIsCorrect() {
        assertEquals(BrokerActionFailed::class, testee.event)
    }

    @Test
    fun whenBrokerActionFailedWithAllowRetryFalseThenCompletesBrokerStepWithFailure() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testClickAction),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            actionRetryCount = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = BrokerActionFailed(error = testError, allowRetry = false)

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        val nextEvent = result.nextEvent as BrokerStepCompleted
        assertEquals(false, nextEvent.needsEmailConfirmation)
        val stepStatus = nextEvent.stepStatus as Failure
        assertEquals(testError, stepStatus.error)
        verify(mockPirRunStateHandler).handleState(any())
    }

    @Test
    fun whenOptOutActionFailedWithRetryCountBelowMaxThenRetries() = runTest {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testClickAction),
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
            actionRetryCount = 1,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = BrokerActionFailed(error = testError, allowRetry = true)

        val result = testee.invoke(state, event)

        assertEquals(0, result.nextState.currentActionIndex)
        assertEquals(2, result.nextState.actionRetryCount)
        val nextEvent = result.nextEvent as ExecuteBrokerStepAction
        assertEquals(testProfileQuery, (nextEvent.actionRequestData as PirScriptRequestData.UserProfile).userProfile)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenOptOutActionFailedAtMaxRetryCountThenFailsBrokerStep() = runTest {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testClickAction),
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
            actionRetryCount = MAX_RETRY_COUNT_OPTOUT,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = BrokerActionFailed(error = testError, allowRetry = true)

        val result = testee.invoke(state, event)

        val nextEvent = result.nextEvent as BrokerStepCompleted
        assertEquals(false, nextEvent.needsEmailConfirmation)
        val stepStatus = nextEvent.stepStatus as Failure
        assertEquals(testError, stepStatus.error)
    }

    @Test
    fun whenScanExpectationActionFailedWithRetryCountBelowMaxThenRetries() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testExpectationAction),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            actionRetryCount = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = BrokerActionFailed(error = testError, allowRetry = true)

        val result = testee.invoke(state, event)

        assertEquals(0, result.nextState.currentActionIndex)
        assertEquals(1, result.nextState.actionRetryCount)
        val nextEvent = result.nextEvent as ExecuteBrokerStepAction
        assertEquals(testProfileQuery, (nextEvent.actionRequestData as PirScriptRequestData.UserProfile).userProfile)
    }

    @Test
    fun whenScanExpectationActionFailedAtMaxRetryCountThenFailsBrokerStep() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testExpectationAction),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            actionRetryCount = MAX_RETRY_COUNT_SCAN,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = BrokerActionFailed(error = testError, allowRetry = true)

        val result = testee.invoke(state, event)

        val nextEvent = result.nextEvent as BrokerStepCompleted
        assertEquals(false, nextEvent.needsEmailConfirmation)
        val stepStatus = nextEvent.stepStatus as Failure
        assertEquals(testError, stepStatus.error)
    }

    @Test
    fun whenScanNonExpectationActionFailedThenFailsImmediately() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testClickAction),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            actionRetryCount = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = BrokerActionFailed(error = testError, allowRetry = true)

        val result = testee.invoke(state, event)

        val nextEvent = result.nextEvent as BrokerStepCompleted
        assertEquals(false, nextEvent.needsEmailConfirmation)
        val stepStatus = nextEvent.stepStatus as Failure
        assertEquals(testError, stepStatus.error)
    }

    @Test
    fun whenEmailConfirmationActionFailedWithRetryCountBelowMaxThenRetries() = runTest {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testClickAction),
                optOutType = "form",
            ),
            profileToOptOut = testExtractedProfile,
        )
        val state = State(
            runType = RunType.EMAIL_CONFIRMATION,
            brokerStepsToExecute = listOf(optOutStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            actionRetryCount = 1,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = BrokerActionFailed(error = testError, allowRetry = true)

        val result = testee.invoke(state, event)

        assertEquals(0, result.nextState.currentActionIndex)
        assertEquals(2, result.nextState.actionRetryCount)
        val nextEvent = result.nextEvent as ExecuteBrokerStepAction
        assertEquals(testProfileQuery, (nextEvent.actionRequestData as PirScriptRequestData.UserProfile).userProfile)
    }

    @Test
    fun whenBrokerActionFailedThenEmitsPixel() = runTest {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testClickAction),
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
            actionRetryCount = MAX_RETRY_COUNT_OPTOUT,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = BrokerActionFailed(error = testError, allowRetry = true)

        testee.invoke(state, event)

        verify(mockPirRunStateHandler).handleState(any())
    }
}
