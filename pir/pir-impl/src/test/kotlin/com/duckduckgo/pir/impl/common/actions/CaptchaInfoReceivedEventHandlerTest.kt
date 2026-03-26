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
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageCaptchaSent
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.CaptchaInfoReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CaptchaInfoReceivedEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: CaptchaInfoReceivedEventHandler
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockPirRunStateHandler: PirRunStateHandler = mock()

    private val testProfileQueryId = 123L
    private val testBrokerName = "test-broker"
    private val testCurrentTimeInMillis = 5000L
    private val testStageStartMs = 2000L

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

    private val testAction = BrokerAction.GetCaptchaInfo(
        id = "captcha-action-1",
        selector = "captcha-selector",
    )

    @Before
    fun setUp() {
        testee = CaptchaInfoReceivedEventHandler(
            mockCurrentTimeProvider,
            mockPirRunStateHandler,
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTimeInMillis)
    }

    @Test
    fun whenEventIsCaptchaInfoReceivedThenEventTypeIsCorrect() {
        assertEquals(CaptchaInfoReceived::class, testee.event)
    }

    @Test
    fun whenCaptchaInfoReceivedWithOptOutStepThenEmitsPixel() = runTest {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testAction),
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
            actionRetryCount = 2,
            attemptId = "attempt-123",
            stageStatus = PirStageStatus(
                currentStage = PirStage.CAPTCHA_SEND,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = CaptchaInfoReceived(transactionID = "transaction-456")

        testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerOptOutStageCaptchaSent>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(testBroker, capturedState.firstValue.broker)
        assertEquals("captcha-action-1", capturedState.firstValue.actionID)
        assertEquals("attempt-123", capturedState.firstValue.attemptId)
        assertEquals(testCurrentTimeInMillis - testStageStartMs, capturedState.firstValue.durationMs)
        assertEquals(3, capturedState.firstValue.currentActionAttemptCount)
    }

    @Test
    fun whenCaptchaInfoReceivedWithScanStepThenDoesNotEmitPixel() = runTest {
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
                currentStage = PirStage.CAPTCHA_SEND,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = CaptchaInfoReceived(transactionID = "transaction-456")

        testee.invoke(state, event)

        verify(mockPirRunStateHandler, never()).handleState(any())
    }

    @Test
    fun whenCaptchaInfoReceivedThenUpdatesStateWithTransactionId() = runTest {
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
            currentActionIndex = 2,
            transactionID = "",
            stageStatus = PirStageStatus(
                currentStage = PirStage.CAPTCHA_SEND,
                stageStartMs = 0,
            ),
        )
        val event = CaptchaInfoReceived(transactionID = "new-transaction-789")

        val result = testee.invoke(state, event)

        assertEquals("new-transaction-789", result.nextState.transactionID)
    }

    @Test
    fun whenCaptchaInfoReceivedThenIncrementsActionIndexAndResetsRetryCount() = runTest {
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
            currentActionIndex = 1,
            actionRetryCount = 3,
            stageStatus = PirStageStatus(
                currentStage = PirStage.CAPTCHA_SEND,
                stageStartMs = 0,
            ),
        )
        val event = CaptchaInfoReceived(transactionID = "transaction-456")

        val result = testee.invoke(state, event)

        assertEquals(2, result.nextState.currentActionIndex)
        assertEquals(0, result.nextState.actionRetryCount)
    }

    @Test
    fun whenCaptchaInfoReceivedThenReturnsExecuteBrokerStepActionEvent() = runTest {
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
            currentActionIndex = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.CAPTCHA_SEND,
                stageStartMs = 0,
            ),
        )
        val event = CaptchaInfoReceived(transactionID = "transaction-456")

        val result = testee.invoke(state, event)

        assertEquals(
            ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
            result.nextEvent,
        )
        assertNull(result.sideEffect)
    }

    @Test
    fun whenCaptchaInfoReceivedThenPreservesOtherStateFields() = runTest {
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
            brokerStepStartTime = 10000L,
            pendingUrl = "https://example.com",
            attemptId = "attempt-xyz",
            stageStatus = PirStageStatus(
                currentStage = PirStage.CAPTCHA_SEND,
                stageStartMs = 5000L,
            ),
        )
        val event = CaptchaInfoReceived(transactionID = "transaction-456")

        val result = testee.invoke(state, event)

        assertEquals(RunType.SCHEDULED, result.nextState.runType)
        assertEquals(0, result.nextState.currentBrokerStepIndex)
        assertEquals(1, result.nextState.currentActionIndex)
        assertEquals(10000L, result.nextState.brokerStepStartTime)
        assertEquals("https://example.com", result.nextState.pendingUrl)
        assertEquals("attempt-xyz", result.nextState.attemptId)
    }
}
