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
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.OptOutStepActions
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageGenerateEmailReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.EmailReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.duckduckgo.pir.impl.store.PirRepository.GeneratedEmailData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EmailReceivedEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: EmailReceivedEventHandler
    private val mockPirRunStateHandler: PirRunStateHandler = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()

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

    private val testAction = BrokerAction.FillForm(
        id = "fillform-action-1",
        elements = emptyList(),
        selector = "form",
    )

    private val testGeneratedEmailData = GeneratedEmailData(
        emailAddress = "generated@example.com",
        pattern = "pattern-123",
    )

    @Before
    fun setUp() {
        testee = EmailReceivedEventHandler(
            mockPirRunStateHandler,
            mockCurrentTimeProvider,
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTimeInMillis)
    }

    @Test
    fun whenEventIsEmailReceivedThenEventTypeIsCorrect() {
        assertEquals(EmailReceived::class, testee.event)
    }

    @Test
    fun whenEmailReceivedThenUpdatesProfileWithEmailAddress() = runTest {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testAction),
                optOutType = "form",
            ),
            profileToOptOut = testExtractedProfile.copy(email = ""),
        )
        val state = State(
            runType = RunType.OPTOUT,
            brokerStepsToExecute = listOf(optOutStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.EMAIL_GENERATE,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        val result = testee.invoke(state, event)

        val updatedOptOutStep = result.nextState.brokerStepsToExecute[0] as OptOutStep
        assertEquals("generated@example.com", updatedOptOutStep.profileToOptOut.email)
    }

    @Test
    fun whenEmailReceivedThenUpdatesGeneratedEmailDataInState() = runTest {
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
            generatedEmailData = null,
            stageStatus = PirStageStatus(
                currentStage = PirStage.EMAIL_GENERATE,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        val result = testee.invoke(state, event)

        assertEquals(testGeneratedEmailData, result.nextState.generatedEmailData)
    }

    @Test
    fun whenEmailReceivedThenReturnsExecuteBrokerStepActionWithExtractedProfile() = runTest {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testAction),
                optOutType = "form",
            ),
            profileToOptOut = testExtractedProfile.copy(email = ""),
        )
        val state = State(
            runType = RunType.OPTOUT,
            brokerStepsToExecute = listOf(optOutStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.EMAIL_GENERATE,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        val result = testee.invoke(state, event)

        val nextEvent = result.nextEvent as ExecuteBrokerStepAction
        val userProfile = nextEvent.actionRequestData as UserProfile
        assertEquals(testProfileQuery, userProfile.userProfile)
        assertEquals("generated@example.com", userProfile.extractedProfile?.email)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenEmailReceivedWithOptOutStepThenEmitsPixel() = runTest {
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
            actionRetryCount = 1,
            attemptId = "attempt-456",
            stageStatus = PirStageStatus(
                currentStage = PirStage.EMAIL_GENERATE,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerOptOutStageGenerateEmailReceived>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(testBroker, capturedState.firstValue.broker)
        assertEquals("fillform-action-1", capturedState.firstValue.actionID)
        assertEquals("attempt-456", capturedState.firstValue.attemptId)
        assertEquals(testCurrentTimeInMillis - testStageStartMs, capturedState.firstValue.durationMs)
        assertEquals(2, capturedState.firstValue.currentActionAttemptCount) // actionRetryCount + 1
    }

    @Test
    fun whenEmailReceivedThenPreservesOtherStateFields() = runTest {
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
            brokerStepStartTime = 10000L,
            transactionID = "transaction-789",
            pendingUrl = "https://example.com",
            actionRetryCount = 1,
            attemptId = "attempt-xyz",
            stageStatus = PirStageStatus(
                currentStage = PirStage.EMAIL_GENERATE,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        val result = testee.invoke(state, event)

        assertEquals(RunType.OPTOUT, result.nextState.runType)
        assertEquals(0, result.nextState.currentBrokerStepIndex)
        assertEquals(0, result.nextState.currentActionIndex)
        assertEquals(10000L, result.nextState.brokerStepStartTime)
        assertEquals("transaction-789", result.nextState.transactionID)
        assertEquals("https://example.com", result.nextState.pendingUrl)
        assertEquals(1, result.nextState.actionRetryCount)
        assertEquals("attempt-xyz", result.nextState.attemptId)
    }

    @Test
    fun whenEmailReceivedWithMultipleBrokerStepsThenOnlyUpdatesCurrentStep() = runTest {
        val optOutStep1 = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testAction),
                optOutType = "form",
            ),
            profileToOptOut = testExtractedProfile.copy(email = ""),
        )
        val optOutStep2 = OptOutStep(
            broker = testBroker.copy(name = "broker-2"),
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testAction),
                optOutType = "form",
            ),
            profileToOptOut = testExtractedProfile.copy(email = "old@example.com"),
        )
        val state = State(
            runType = RunType.OPTOUT,
            brokerStepsToExecute = listOf(optOutStep1, optOutStep2),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.EMAIL_GENERATE,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        val result = testee.invoke(state, event)

        val updatedOptOutStep1 = result.nextState.brokerStepsToExecute[0] as OptOutStep
        val unchangedOptOutStep2 = result.nextState.brokerStepsToExecute[1] as OptOutStep
        assertEquals("generated@example.com", updatedOptOutStep1.profileToOptOut.email)
        assertEquals("old@example.com", unchangedOptOutStep2.profileToOptOut.email)
    }
}
