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
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageGenerateEmailReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted.StepStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.EmailReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
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
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.duckduckgo.pir.impl.store.PirRepository.GeneratedEmailData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
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

    private val testFillFormAction = BrokerAction.FillForm(
        id = "fillform-action-1",
        elements = emptyList(),
        selector = "form",
    )

    private val testGenerateEmailAction = BrokerAction.GenerateEmail(
        id = "generate-email-action-1",
    )

    private val testGeneratedEmailData = GeneratedEmailData(
        emailAddress = "generated@example.com",
        pattern = "pattern-123",
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

    // region Implicit needsEmail flow (FillForm action)

    @Test
    fun whenEmailReceivedForFillFormOnOptOutStepThenUpdatesGeneratedEmailDataInState() = runTest {
        val state = createOptOutState(testFillFormAction)
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        val result = testee.invoke(state, event)

        assertEquals(testGeneratedEmailData, result.nextState.generatedEmailData)
    }

    @Test
    fun whenEmailReceivedForFillFormOnOptOutStepThenKeepsSameActionIndex() = runTest {
        val state = createOptOutState(testFillFormAction)
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        val result = testee.invoke(state, event)

        assertEquals(0, result.nextState.currentActionIndex)
    }

    @Test
    fun whenEmailReceivedForFillFormOnOptOutStepThenReturnsExtractedProfileWithEmail() = runTest {
        val state = createOptOutState(testFillFormAction)
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        val result = testee.invoke(state, event)

        val nextEvent = result.nextEvent as ExecuteBrokerStepAction
        val userProfile = nextEvent.actionRequestData as UserProfile
        assertEquals(testProfileQuery, userProfile.userProfile)
        assertEquals("generated@example.com", userProfile.extractedProfile?.email)
        assertEquals("John Doe", userProfile.extractedProfile?.name)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenEmailReceivedForFillFormOnOptOutStepThenDoesNotModifyBrokerSteps() = runTest {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testFillFormAction),
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

        // Broker steps should be unchanged — profileToOptOut.email is NOT updated (KDL3)
        val unchangedOptOutStep = result.nextState.brokerStepsToExecute[0] as OptOutStep
        assertEquals("", unchangedOptOutStep.profileToOptOut.email)
    }

    @Test
    fun whenEmailReceivedForFillFormOnEmailConfirmationStepThenKeepsSameActionIndex() = runTest {
        val emailConfirmationStep = EmailConfirmationStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testFillFormAction),
                optOutType = "form",
            ),
            emailConfirmationJob = testEmailConfirmationJob,
            profileToOptOut = testExtractedProfile.copy(email = ""),
        )
        val state = State(
            runType = RunType.EMAIL_CONFIRMATION,
            brokerStepsToExecute = listOf(emailConfirmationStep),
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

        assertEquals(0, result.nextState.currentActionIndex)
        assertEquals(testGeneratedEmailData, result.nextState.generatedEmailData)
        val nextEvent = result.nextEvent as ExecuteBrokerStepAction
        val userProfile = nextEvent.actionRequestData as UserProfile
        assertEquals("generated@example.com", userProfile.extractedProfile?.email)
    }

    @Test
    fun whenEmailReceivedForFillFormOnScanStepThenFailsGracefully() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testFillFormAction),
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
                currentStage = PirStage.EMAIL_GENERATE,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        val result = testee.invoke(state, event)

        val nextEvent = result.nextEvent as BrokerStepCompleted
        assertEquals(false, nextEvent.needsEmailConfirmation)
        assertTrue(nextEvent.stepStatus is StepStatus.Failure)
    }

    // endregion

    // region Explicit GenerateEmail flow

    @Test
    fun whenEmailReceivedForGenerateEmailOnOptOutStepThenAdvancesActionIndex() = runTest {
        val fillFormAction = BrokerAction.FillForm(id = "fillform-2", elements = emptyList(), selector = "form")
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testGenerateEmailAction, fillFormAction),
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
            stageStatus = PirStageStatus(
                currentStage = PirStage.EMAIL_GENERATE,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        val result = testee.invoke(state, event)

        assertEquals(1, result.nextState.currentActionIndex)
        assertEquals(testGeneratedEmailData, result.nextState.generatedEmailData)
        val nextEvent = result.nextEvent as ExecuteBrokerStepAction
        val userProfile = nextEvent.actionRequestData as UserProfile
        assertNull(userProfile.extractedProfile)
    }

    @Test
    fun whenEmailReceivedForGenerateEmailOnScanStepThenAdvancesActionIndex() = runTest {
        val fillFormAction = BrokerAction.FillForm(id = "fillform-2", elements = emptyList(), selector = "form")
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testGenerateEmailAction, fillFormAction),
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
                currentStage = PirStage.EMAIL_GENERATE,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        val result = testee.invoke(state, event)

        assertEquals(1, result.nextState.currentActionIndex)
        assertEquals(testGeneratedEmailData, result.nextState.generatedEmailData)
        val nextEvent = result.nextEvent as ExecuteBrokerStepAction
        val userProfile = nextEvent.actionRequestData as UserProfile
        assertNull(userProfile.extractedProfile)
    }

    @Test
    fun whenEmailReceivedForGenerateEmailOnEmailConfirmationStepThenAdvancesActionIndex() = runTest {
        val fillFormAction = BrokerAction.FillForm(id = "fillform-2", elements = emptyList(), selector = "form")
        val emailConfirmationStep = EmailConfirmationStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testGenerateEmailAction, fillFormAction),
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
            stageStatus = PirStageStatus(
                currentStage = PirStage.EMAIL_GENERATE,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        val result = testee.invoke(state, event)

        assertEquals(1, result.nextState.currentActionIndex)
        assertEquals(testGeneratedEmailData, result.nextState.generatedEmailData)
    }

    // endregion

    // region Pixel emission

    @Test
    fun whenEmailReceivedWithOptOutStepThenEmitsPixel() = runTest {
        val state = createOptOutState(testFillFormAction, actionRetryCount = 1, attemptId = "attempt-456")
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerOptOutStageGenerateEmailReceived>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(testBroker, capturedState.firstValue.broker)
        assertEquals("fillform-action-1", capturedState.firstValue.actionID)
        assertEquals("attempt-456", capturedState.firstValue.attemptId)
        assertEquals(testCurrentTimeInMillis - testStageStartMs, capturedState.firstValue.durationMs)
        assertEquals(2, capturedState.firstValue.currentActionAttemptCount)
    }

    @Test
    fun whenEmailReceivedWithScanStepThenDoesNotEmitPixel() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testGenerateEmailAction),
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
                currentStage = PirStage.EMAIL_GENERATE,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = EmailReceived(generatedEmailData = testGeneratedEmailData)

        testee.invoke(state, event)

        verifyNoInteractions(mockPirRunStateHandler)
    }

    // endregion

    // region State preservation

    @Test
    fun whenEmailReceivedThenPreservesOtherStateFields() = runTest {
        val state = State(
            runType = RunType.OPTOUT,
            brokerStepsToExecute = listOf(
                OptOutStep(
                    broker = testBroker,
                    step = OptOutStepActions(
                        stepType = "optout",
                        actions = listOf(testFillFormAction),
                        optOutType = "form",
                    ),
                    profileToOptOut = testExtractedProfile,
                ),
            ),
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
    fun whenEmailReceivedWithMultipleBrokerStepsThenDoesNotModifyAnyStep() = runTest {
        val optOutStep1 = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testFillFormAction),
                optOutType = "form",
            ),
            profileToOptOut = testExtractedProfile.copy(email = ""),
        )
        val optOutStep2 = OptOutStep(
            broker = testBroker.copy(name = "broker-2"),
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testFillFormAction),
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

        // Neither step should be modified
        val unchangedStep1 = result.nextState.brokerStepsToExecute[0] as OptOutStep
        val unchangedStep2 = result.nextState.brokerStepsToExecute[1] as OptOutStep
        assertEquals("", unchangedStep1.profileToOptOut.email)
        assertEquals("old@example.com", unchangedStep2.profileToOptOut.email)
    }

    // endregion

    private fun createOptOutState(
        action: BrokerAction,
        actionRetryCount: Int = 0,
        attemptId: String = "",
    ): State {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(action),
                optOutType = "form",
            ),
            profileToOptOut = testExtractedProfile.copy(email = ""),
        )
        return State(
            runType = RunType.OPTOUT,
            brokerStepsToExecute = listOf(optOutStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            actionRetryCount = actionRetryCount,
            attemptId = attemptId,
            stageStatus = PirStageStatus(
                currentStage = PirStage.EMAIL_GENERATE,
                stageStartMs = testStageStartMs,
            ),
        )
    }
}
