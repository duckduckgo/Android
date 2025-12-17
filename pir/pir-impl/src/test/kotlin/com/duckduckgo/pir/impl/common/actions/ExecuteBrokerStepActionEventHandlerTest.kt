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
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageCaptchaSolved
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageSubmit
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanActionStarted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted.StepStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.AwaitCaptchaSolution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.GetEmailForProfile
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.LoadUrl
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.PushJsAction
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
import com.duckduckgo.pir.impl.scripts.models.DataSource
import com.duckduckgo.pir.impl.scripts.models.ElementSelector
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
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
import org.mockito.kotlin.whenever

class ExecuteBrokerStepActionEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: ExecuteBrokerStepActionEventHandler
    private val mockPirRunStateHandler: PirRunStateHandler = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()

    private val testCurrentTimeInMillis = 10000L
    private val testStageStartMs = 8000L
    private val testProfileQueryId = 123L
    private val testBrokerName = "test-broker"

    private val testBroker = Broker(
        name = testBrokerName,
        fileName = "test-broker.json",
        url = "https://test-broker.com",
        version = "1.0",
        parent = null,
        addedDatetime = 124354,
        removedAt = 0L,
    )

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
            email = "john@example.com",
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
        testee = ExecuteBrokerStepActionEventHandler(
            mockPirRunStateHandler,
            mockCurrentTimeProvider,
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTimeInMillis)
    }

    @Test
    fun whenEventIsExecuteBrokerStepActionThenEventTypeIsCorrect() {
        assertEquals(ExecuteBrokerStepAction::class, testee.event)
    }

    @Test
    fun whenAllActionsCompletedThenReturnsBrokerStepCompletedSuccess() = runTest {
        val action1 = BrokerAction.Navigate(id = "action-1", url = "https://example.com")
        val action2 = BrokerAction.Click(id = "action-2", elements = emptyList(), selector = null)
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(action1, action2),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 2,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery))

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        val nextEvent = result.nextEvent as BrokerStepCompleted
        assertEquals(false, nextEvent.needsEmailConfirmation)
        assertTrue(nextEvent.stepStatus is StepStatus.Success)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenOptOutActionNeedsEmailAndNoEmailThenRequestsEmailGeneration() = runTest {
        val action = BrokerAction.FillForm(
            id = "action-1",
            elements = listOf(
                ElementSelector(
                    type = "email",
                    selector = "input[name='email']",
                    parent = null,
                    multiple = null,
                    min = null,
                    max = null,
                    failSilently = null,
                ),
            ),
            selector = "form",
        )
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(action),
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
                currentStage = PirStage.OTHER,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery))

        val result = testee.invoke(state, event)

        assertEquals(PirStage.EMAIL_GENERATE, result.nextState.stageStatus.currentStage)
        assertEquals(testCurrentTimeInMillis, result.nextState.stageStatus.stageStartMs)
        val sideEffect = result.sideEffect as GetEmailForProfile
        assertEquals("action-1", sideEffect.actionId)
        assertEquals(testBrokerName, sideEffect.brokerName)
        assertEquals(testExtractedProfile.copy(email = ""), sideEffect.extractedProfile)
        assertEquals(testProfileQuery, sideEffect.profileQuery)
        assertNull(result.nextEvent)
    }

    @Test
    fun whenEmailConfirmationActionNeedsEmailAndNoEmailThenRequestsEmailGeneration() = runTest {
        val action = BrokerAction.FillForm(
            id = "action-1",
            elements = listOf(
                ElementSelector(
                    type = "email",
                    selector = "input[name='email']",
                    parent = null,
                    multiple = null,
                    min = null,
                    max = null,
                    failSilently = null,
                ),
            ),
            selector = "form",
        )
        val emailConfirmationStep = EmailConfirmationStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(action),
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
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery))

        val result = testee.invoke(state, event)

        assertEquals(PirStage.EMAIL_GENERATE, result.nextState.stageStatus.currentStage)
        assertTrue(result.sideEffect is GetEmailForProfile)
    }

    @Test
    fun whenOptOutEmailConfirmationActionThenEmitsPixelAndCompletes() = runTest {
        val action = BrokerAction.EmailConfirmation(id = "action-confirm", pollingTime = "5000")
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(action),
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
            attemptId = "attempt-123",
            stageStatus = PirStageStatus(
                currentStage = PirStage.FILL_FORM,
                stageStartMs = testStageStartMs,
            ),
        )
        val event = ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery))

        val result = testee.invoke(state, event)

        // Verify pixel emission
        val capturedPixel = argumentCaptor<BrokerOptOutStageSubmit>()
        verify(mockPirRunStateHandler).handleState(capturedPixel.capture())
        assertEquals(testBroker, capturedPixel.firstValue.broker)
        assertEquals("action-confirm", capturedPixel.firstValue.actionID)
        assertEquals("attempt-123", capturedPixel.firstValue.attemptId)
        assertEquals(testCurrentTimeInMillis - testStageStartMs, capturedPixel.firstValue.durationMs)
        assertEquals(2, capturedPixel.firstValue.currentActionAttemptCount)

        // Verify state and event
        assertEquals(PirStage.EMAIL_CONFIRM_HALTED, result.nextState.stageStatus.currentStage)
        val nextEvent = result.nextEvent as BrokerStepCompleted
        assertEquals(true, nextEvent.needsEmailConfirmation)
        assertTrue(nextEvent.stepStatus is StepStatus.Success)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenEmailConfirmationStepWithValidLinkThenLoadsConfirmationUrl() = runTest {
        val action = BrokerAction.EmailConfirmation(id = "action-confirm", pollingTime = "5000")
        val emailConfirmationStep = EmailConfirmationStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(action),
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
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery))

        val result = testee.invoke(state, event)

        assertEquals("https://example.com/confirm", result.nextState.pendingUrl)
        val sideEffect = result.sideEffect as LoadUrl
        assertEquals("https://example.com/confirm", sideEffect.url)
        assertNull(result.nextEvent)
    }

    @Test
    fun whenEmailConfirmationStepWithEmptyLinkThenFailsStep() = runTest {
        val action = BrokerAction.EmailConfirmation(id = "action-confirm", pollingTime = "5000")
        val jobWithEmptyLink = testEmailConfirmationJob.copy(
            linkFetchData = LinkFetchData(
                emailConfirmationLink = "",
                linkFetchAttemptCount = 0,
                lastLinkFetchDateInMillis = 0L,
            ),
        )
        val emailConfirmationStep = EmailConfirmationStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(action),
                optOutType = "form",
            ),
            emailConfirmationJob = jobWithEmptyLink,
            profileToOptOut = testExtractedProfile,
        )
        val state = State(
            runType = RunType.EMAIL_CONFIRMATION,
            brokerStepsToExecute = listOf(emailConfirmationStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery))

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        val nextEvent = result.nextEvent as BrokerStepCompleted
        assertEquals(true, nextEvent.needsEmailConfirmation)
        assertTrue(nextEvent.stepStatus is StepStatus.Failure)
        val failure = nextEvent.stepStatus as StepStatus.Failure
        assertTrue(failure.error is PirError.Unknown)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenSolveCaptchaActionWithoutSolutionThenRequestsCaptchaSolution() = runTest {
        val action = BrokerAction.SolveCaptcha(id = "action-captcha", selector = "captcha-input")
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(action),
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
            transactionID = "txn-123",
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery))

        val result = testee.invoke(state, event)

        assertEquals(PirStage.CAPTCHA_SOLVE, result.nextState.stageStatus.currentStage)
        assertEquals(testCurrentTimeInMillis, result.nextState.stageStatus.stageStartMs)
        val sideEffect = result.sideEffect as AwaitCaptchaSolution
        assertEquals("action-captcha", sideEffect.actionId)
        assertEquals(testBrokerName, sideEffect.brokerName)
        assertEquals("txn-123", sideEffect.transactionID)
        assertEquals(0, sideEffect.attempt)
        assertNull(result.nextEvent)
    }

    @Test
    fun whenSolveCaptchaActionWithSolutionThenPushesToJs() = runTest {
        val action = BrokerAction.SolveCaptcha(id = "action-captcha", selector = "captcha-input")
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(action),
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
            actionRetryCount = 0,
            attemptId = "attempt-456",
            stageStatus = PirStageStatus(
                currentStage = PirStage.CAPTCHA_SOLVE,
                stageStartMs = testStageStartMs,
            ),
        )
        val solveCaptchaData = PirScriptRequestData.SolveCaptcha(
            token = "captcha-token",
        )
        val event = ExecuteBrokerStepAction(solveCaptchaData)

        val result = testee.invoke(state, event)

        // Verify pixel emission
        val capturedPixel = argumentCaptor<BrokerOptOutStageCaptchaSolved>()
        verify(mockPirRunStateHandler).handleState(capturedPixel.capture())
        assertEquals(testBroker, capturedPixel.firstValue.broker)
        assertEquals("action-captcha", capturedPixel.firstValue.actionID)
        assertEquals("attempt-456", capturedPixel.firstValue.attemptId)
        assertEquals(testCurrentTimeInMillis - testStageStartMs, capturedPixel.firstValue.durationMs)
        assertEquals(1, capturedPixel.firstValue.currentActionAttemptCount)

        // Verify PushJsAction
        val sideEffect = result.sideEffect as PushJsAction
        assertEquals("action-captcha", sideEffect.actionId)
        assertEquals(action, sideEffect.action)
        assertNull(result.nextEvent)
    }

    @Test
    fun whenScanStepNavigateActionThenEmitsPixelAndPushesToJs() = runTest {
        val action = BrokerAction.Navigate(id = "action-nav", url = "https://example.com")
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(action),
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
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery))

        val result = testee.invoke(state, event)

        // Verify pixel emission
        val capturedPixel = argumentCaptor<BrokerScanActionStarted>()
        verify(mockPirRunStateHandler).handleState(capturedPixel.capture())
        assertEquals(testBroker, capturedPixel.firstValue.broker)
        assertEquals(testProfileQueryId, capturedPixel.firstValue.profileQueryId)
        assertEquals(3, capturedPixel.firstValue.currentActionAttemptCount)
        assertEquals(action, capturedPixel.firstValue.currentAction)

        // Verify PushJsAction
        val sideEffect = result.sideEffect as PushJsAction
        assertEquals("action-nav", sideEffect.actionId)
        assertEquals(action, sideEffect.action)
        assertNull(result.nextEvent)
    }

    @Test
    fun whenClickActionThen10SecondDelay() = runTest {
        val action = BrokerAction.Click(id = "action-click", elements = emptyList(), selector = null)
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(action),
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
        val event = ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery))

        val result = testee.invoke(state, event)

        val sideEffect = result.sideEffect as PushJsAction
        assertEquals(10_000L, sideEffect.pushDelay)
        assertEquals(PirStage.FILL_FORM, result.nextState.stageStatus.currentStage)
    }

    @Test
    fun whenExpectationActionThen10SecondDelay() = runTest {
        val action = BrokerAction.Expectation(id = "action-expect", expectations = emptyList())
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(action),
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
        val event = ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery))

        val result = testee.invoke(state, event)

        val sideEffect = result.sideEffect as PushJsAction
        assertEquals(10_000L, sideEffect.pushDelay)
        assertEquals(PirStage.SUBMIT, result.nextState.stageStatus.currentStage)
    }

    @Test
    fun whenOptOutFillFormActionThen5SecondDelay() = runTest {
        val action = BrokerAction.FillForm(id = "action-fill", elements = emptyList(), selector = "form")
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(action),
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
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery))

        val result = testee.invoke(state, event)

        val sideEffect = result.sideEffect as PushJsAction
        assertEquals(5_000L, sideEffect.pushDelay)
        assertEquals(PirStage.FILL_FORM, result.nextState.stageStatus.currentStage)
    }

    @Test
    fun whenGetCaptchaInfoActionThenStageIsCaptchaParse() = runTest {
        val action = BrokerAction.GetCaptchaInfo(id = "action-captcha", selector = "captcha")
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(action),
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
        val event = ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery))

        val result = testee.invoke(state, event)

        assertEquals(PirStage.CAPTCHA_PARSE, result.nextState.stageStatus.currentStage)
        assertEquals(testCurrentTimeInMillis, result.nextState.stageStatus.stageStartMs)
    }

    @Test
    fun whenOptOutActionNeedsExtractedProfileThenIncludesIt() = runTest {
        val action = BrokerAction.FillForm(
            id = "action-fill",
            elements = emptyList(),
            selector = "form",
            dataSource = DataSource.EXTRACTED_PROFILE,
        )
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(action),
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
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery))

        val result = testee.invoke(state, event)

        val sideEffect = result.sideEffect as PushJsAction
        val userData = sideEffect.requestParamsData as UserProfile
        assertEquals(testProfileQuery, userData.userProfile)
        assertEquals("John Doe", userData.extractedProfile?.name)
        assertEquals("john@example.com", userData.extractedProfile?.email)
    }
}
