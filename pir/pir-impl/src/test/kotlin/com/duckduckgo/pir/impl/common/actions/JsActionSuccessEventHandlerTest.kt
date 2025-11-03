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
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutActionSucceeded
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanActionSucceeded
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.JsActionSuccess
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.EvaluateJs
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.GetCaptchaSolution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.LoadUrl
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.EmailData
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.JobAttemptData
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.LinkFetchData
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ClickResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ConditionResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExpectationResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.ExtractedResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.FillFormResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.GetCaptchaInfoResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.NavigateResponse
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.SolveCaptchaResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JsActionSuccessEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: JsActionSuccessEventHandler

    private val mockPirRunStateHandler: PirRunStateHandler = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()

    // Test data
    private val testBrokerName = "test-broker"
    private val testProfileQueryId = 123L
    private val testCurrentTimeInMillis = 2000L
    private val testCurrentActionIndex = 1
    private val testActionRetryCount = 3

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

    private val testAction = BrokerAction.Navigate(
        id = "action-1",
        url = "https://example.com",
    )

    @Before
    fun setUp() {
        testee =
            JsActionSuccessEventHandler(
                pirRunStateHandler = mockPirRunStateHandler,
                currentTimeProvider = mockCurrentTimeProvider,
            )

        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTimeInMillis)
    }

    @Test
    fun whenEventIsJsActionSuccessThenEventTypeIsCorrect() {
        assertEquals(JsActionSuccess::class, testee.event)
    }

    @Test
    fun whenNavigateResponseWithScanStepThenReturnsLoadUrlSideEffect() = runTest {
        val navigateResponse =
            NavigateResponse(
                actionID = "navigate-1",
                actionType = "navigate",
                response = NavigateResponse.ResponseData(url = "https://example.com/result"),
            )
        val scanStep =
            ScanStep(
                brokerName = testBrokerName,
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            )
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = listOf(scanStep),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = testCurrentActionIndex,
                actionRetryCount = testActionRetryCount,
            )
        val event = JsActionSuccess(pirSuccessResponse = navigateResponse)

        val result = testee.invoke(state, event)

        val expectedState =
            state.copy(
                pendingUrl = "https://example.com/result",
                actionRetryCount = 0,
            )
        assertEquals(expectedState, result.nextState)
        assertEquals(LoadUrl(url = "https://example.com/result"), result.sideEffect)
        assertNull(result.nextEvent)

        val capturedState = argumentCaptor<BrokerScanActionSucceeded>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(testBrokerName, capturedState.firstValue.brokerName)
        assertEquals(testProfileQueryId, capturedState.firstValue.profileQueryId)
        assertEquals(navigateResponse, capturedState.firstValue.pirSuccessResponse)
    }

    @Test
    fun whenFillFormResponseWithScanStepThenReturnsNextActionEvent() = runTest {
        val fillFormResponse =
            FillFormResponse(
                actionID = "fillform-1",
                actionType = "fillForm",
            )
        val scanStep =
            ScanStep(
                brokerName = testBrokerName,
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            )
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = listOf(scanStep),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = testCurrentActionIndex,
                actionRetryCount = testActionRetryCount,
            )
        val event = JsActionSuccess(pirSuccessResponse = fillFormResponse)

        val result = testee.invoke(state, event)

        val expectedState =
            state.copy(
                currentActionIndex = testCurrentActionIndex + 1,
                actionRetryCount = 0,
            )
        assertEquals(expectedState, result.nextState)
        assertEquals(
            ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
            result.nextEvent,
        )
        assertNull(result.sideEffect)

        verify(mockPirRunStateHandler).handleState(any<BrokerScanActionSucceeded>())
    }

    @Test
    fun whenClickResponseWithScanStepThenReturnsNextActionEvent() = runTest {
        val clickResponse =
            ClickResponse(
                actionID = "click-1",
                actionType = "click",
            )
        val scanStep =
            ScanStep(
                brokerName = testBrokerName,
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            )
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = listOf(scanStep),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = testCurrentActionIndex,
                actionRetryCount = testActionRetryCount,
            )
        val event = JsActionSuccess(pirSuccessResponse = clickResponse)

        val result = testee.invoke(state, event)

        val expectedState =
            state.copy(
                currentActionIndex = testCurrentActionIndex + 1,
                actionRetryCount = 0,
            )
        assertEquals(expectedState, result.nextState)
        assertEquals(
            ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
            result.nextEvent,
        )
        assertNull(result.sideEffect)
    }

    @Test
    fun whenExpectationResponseWithScanStepThenReturnsNextActionEvent() = runTest {
        val expectationResponse =
            ExpectationResponse(
                actionID = "expectation-1",
                actionType = "expectation",
            )
        val scanStep =
            ScanStep(
                brokerName = testBrokerName,
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            )
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = listOf(scanStep),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = testCurrentActionIndex,
                actionRetryCount = testActionRetryCount,
            )
        val event = JsActionSuccess(pirSuccessResponse = expectationResponse)

        val result = testee.invoke(state, event)

        val expectedState =
            state.copy(
                currentActionIndex = testCurrentActionIndex + 1,
                actionRetryCount = 0,
            )
        assertEquals(expectedState, result.nextState)
        assertEquals(
            ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
            result.nextEvent,
        )
        assertNull(result.sideEffect)
    }

    @Test
    fun whenExtractedResponseWithScanStepThenReturnsNextActionEvent() = runTest {
        val extractedResponse =
            ExtractedResponse(
                actionID = "extract-1",
                actionType = "extract",
                response = emptyList(),
            )
        val scanStep =
            ScanStep(
                brokerName = testBrokerName,
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            )
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = listOf(scanStep),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = testCurrentActionIndex,
                actionRetryCount = testActionRetryCount,
            )
        val event = JsActionSuccess(pirSuccessResponse = extractedResponse)

        val result = testee.invoke(state, event)

        val expectedState =
            state.copy(
                currentActionIndex = testCurrentActionIndex + 1,
                actionRetryCount = 0,
            )
        assertEquals(expectedState, result.nextState)
        assertEquals(
            ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
            result.nextEvent,
        )
        assertNull(result.sideEffect)
    }

    @Test
    fun whenGetCaptchaInfoResponseWithScanStepThenReturnsGetCaptchaSolutionSideEffect() =
        runTest {
            val captchaResponse =
                GetCaptchaInfoResponse(
                    actionID = "captcha-info-1",
                    actionType = "getCaptchaInfo",
                    response =
                    GetCaptchaInfoResponse.ResponseData(
                        siteKey = "test-site-key",
                        url = "https://example.com",
                        type = "recaptcha",
                    ),
                )
            val scanStep =
                ScanStep(
                    brokerName = testBrokerName,
                    stepType = "scan",
                    actions = listOf(testAction),
                    scanType = "initial",
                )
            val state =
                State(
                    runType = RunType.MANUAL,
                    brokerStepsToExecute = listOf(scanStep),
                    profileQuery = testProfileQuery,
                    currentBrokerStepIndex = 0,
                    currentActionIndex = testCurrentActionIndex,
                    actionRetryCount = testActionRetryCount,
                )
            val event = JsActionSuccess(pirSuccessResponse = captchaResponse)

            val result = testee.invoke(state, event)

            val expectedState = state.copy(actionRetryCount = 0)
            assertEquals(expectedState, result.nextState)
            assertEquals(
                GetCaptchaSolution(
                    actionId = "captcha-info-1",
                    responseData = captchaResponse.response,
                    isRetry = false,
                ),
                result.sideEffect,
            )
            assertNull(result.nextEvent)
        }

    @Test
    fun whenSolveCaptchaResponseWithScanStepThenReturnsEvaluateJsSideEffectAndNextEvent() =
        runTest {
            val solveCaptchaResponse =
                SolveCaptchaResponse(
                    actionID = "solve-captcha-1",
                    actionType = "solveCaptcha",
                    response =
                    SolveCaptchaResponse.ResponseData(
                        callback = SolveCaptchaResponse.CallbackData(eval = "callback-script"),
                    ),
                )
            val scanStep =
                ScanStep(
                    brokerName = testBrokerName,
                    stepType = "scan",
                    actions = listOf(testAction),
                    scanType = "initial",
                )
            val state =
                State(
                    runType = RunType.MANUAL,
                    brokerStepsToExecute = listOf(scanStep),
                    profileQuery = testProfileQuery,
                    currentBrokerStepIndex = 0,
                    currentActionIndex = testCurrentActionIndex,
                    actionRetryCount = testActionRetryCount,
                )
            val event = JsActionSuccess(pirSuccessResponse = solveCaptchaResponse)

            val result = testee.invoke(state, event)

            val expectedState =
                state.copy(
                    currentActionIndex = testCurrentActionIndex + 1,
                    actionRetryCount = 0,
                )
            assertEquals(expectedState, result.nextState)
            assertEquals(EvaluateJs(callback = "callback-script"), result.sideEffect)
            assertEquals(
                ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
                result.nextEvent,
            )
        }

    @Test
    fun whenConditionResponseWithActionsThenReturnsConditionExpectationSucceededEvent() =
        runTest {
            val conditionActions =
                listOf(
                    BrokerAction.Click(
                        id = "new-action-1",
                        elements = emptyList(),
                        selector = null,
                    ),
                )
            val conditionResponse =
                ConditionResponse(
                    actionID = "condition-1",
                    actionType = "condition",
                    response = ConditionResponse.ResponseData(actions = conditionActions),
                )
            val scanStep =
                ScanStep(
                    brokerName = testBrokerName,
                    stepType = "scan",
                    actions = listOf(testAction),
                    scanType = "initial",
                )
            val state =
                State(
                    runType = RunType.MANUAL,
                    brokerStepsToExecute = listOf(scanStep),
                    profileQuery = testProfileQuery,
                    currentBrokerStepIndex = 0,
                    currentActionIndex = testCurrentActionIndex,
                    actionRetryCount = testActionRetryCount,
                )
            val event = JsActionSuccess(pirSuccessResponse = conditionResponse)

            val result = testee.invoke(state, event)

            val expectedState = state.copy(actionRetryCount = 0)
            assertEquals(expectedState, result.nextState)
            assertEquals(
                PirActionsRunnerStateEngine.Event.ConditionExpectationSucceeded(conditionActions),
                result.nextEvent,
            )
            assertNull(result.sideEffect)
        }

    @Test
    fun whenConditionResponseWithEmptyActionsThenReturnsNextActionEvent() = runTest {
        val conditionResponse =
            ConditionResponse(
                actionID = "condition-1",
                actionType = "condition",
                response = ConditionResponse.ResponseData(actions = emptyList()),
            )
        val scanStep =
            ScanStep(
                brokerName = testBrokerName,
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            )
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = listOf(scanStep),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = testCurrentActionIndex,
                actionRetryCount = testActionRetryCount,
            )
        val event = JsActionSuccess(pirSuccessResponse = conditionResponse)

        val result = testee.invoke(state, event)

        val expectedState =
            state.copy(
                currentActionIndex = testCurrentActionIndex + 1,
                actionRetryCount = 0,
            )
        assertEquals(expectedState, result.nextState)
        assertEquals(
            ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
            result.nextEvent,
        )
    }

    @Test
    fun whenOptOutStepThenHandlesBrokerOptOutActionSucceeded() = runTest {
        val navigateResponse =
            NavigateResponse(
                actionID = "navigate-1",
                actionType = "navigate",
                response = NavigateResponse.ResponseData(url = "https://example.com/result"),
            )
        val optOutStep =
            OptOutStep(
                brokerName = testBrokerName,
                stepType = "optout",
                actions = listOf(testAction),
                optOutType = "form",
                profileToOptOut = testExtractedProfile,
            )
        val state =
            State(
                runType = RunType.OPTOUT,
                brokerStepsToExecute = listOf(optOutStep),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = testCurrentActionIndex,
                actionRetryCount = testActionRetryCount,
            )
        val event = JsActionSuccess(pirSuccessResponse = navigateResponse)

        val result = testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerOptOutActionSucceeded>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(testBrokerName, capturedState.firstValue.brokerName)
        assertEquals(testExtractedProfile, capturedState.firstValue.extractedProfile)
        assertEquals(testCurrentTimeInMillis, capturedState.firstValue.completionTimeInMillis)
        assertEquals("navigate", capturedState.firstValue.actionType)
        assertEquals(navigateResponse, capturedState.firstValue.result)

        assertEquals(LoadUrl(url = "https://example.com/result"), result.sideEffect)
    }

    @Test
    fun whenEmailConfirmationStepThenHandlesBrokerOptOutActionSucceeded() = runTest {
        val navigateResponse =
            NavigateResponse(
                actionID = "navigate-1",
                actionType = "navigate",
                response = NavigateResponse.ResponseData(url = "https://example.com/result"),
            )
        val emailConfirmationStep =
            EmailConfirmationStep(
                brokerName = testBrokerName,
                stepType = "emailConfirmation",
                actions = listOf(testAction),
                emailConfirmationJob = testEmailConfirmationJob,
                profileToOptOut = testExtractedProfile,
            )
        val state =
            State(
                runType = RunType.EMAIL_CONFIRMATION,
                brokerStepsToExecute = listOf(emailConfirmationStep),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = testCurrentActionIndex,
                actionRetryCount = testActionRetryCount,
            )
        val event = JsActionSuccess(pirSuccessResponse = navigateResponse)

        val result = testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerOptOutActionSucceeded>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(testBrokerName, capturedState.firstValue.brokerName)
        assertEquals(testExtractedProfile, capturedState.firstValue.extractedProfile)
        assertEquals(testCurrentTimeInMillis, capturedState.firstValue.completionTimeInMillis)
        assertEquals("navigate", capturedState.firstValue.actionType)
        assertEquals(navigateResponse, capturedState.firstValue.result)

        assertEquals(LoadUrl(url = "https://example.com/result"), result.sideEffect)
    }
}
