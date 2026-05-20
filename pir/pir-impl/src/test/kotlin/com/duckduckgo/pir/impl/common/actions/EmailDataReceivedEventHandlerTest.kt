/*
 * Copyright (c) 2026 DuckDuckGo
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
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.EmailConfirmationStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.OptOutStepActions
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerOptOutStageEmailGetDataReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.EmailDataReceived
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class EmailDataReceivedEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: EmailDataReceivedEventHandler
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

    private val testGetEmailDataAction = BrokerAction.GetEmailData(
        id = "get-email-data-action-1",
        pollingTime = "5",
        extract = listOf("verificationCode"),
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
        testee = EmailDataReceivedEventHandler(
            mockPirRunStateHandler,
            mockCurrentTimeProvider,
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTimeInMillis)
    }

    @Test
    fun whenEventIsEmailDataReceivedThenEventTypeIsCorrect() {
        assertEquals(EmailDataReceived::class, testee.event)
    }

    @Test
    fun whenEmailDataReceivedThenStoresExtractedDataInState() = runTest {
        val extractedData = mapOf("verificationCode" to "123456")
        val event = EmailDataReceived(emailExtractedData = extractedData)

        val result = testee.invoke(stateForOptOut(), event)

        assertEquals(extractedData, result.nextState.emailExtractedData)
    }

    @Test
    fun whenEmailDataReceivedThenAdvancesActionIndex() = runTest {
        val event = EmailDataReceived(emailExtractedData = mapOf("verificationCode" to "abc"))

        val result = testee.invoke(stateForOptOut(currentActionIndex = 2), event)

        assertEquals(3, result.nextState.currentActionIndex)
    }

    @Test
    fun whenEmailDataReceivedThenDispatchesExecuteBrokerStepActionWithProfileQuery() = runTest {
        val event = EmailDataReceived(emailExtractedData = mapOf("verificationCode" to "abc"))

        val result = testee.invoke(stateForOptOut(), event)

        val nextEvent = result.nextEvent as ExecuteBrokerStepAction
        val userProfile = nextEvent.actionRequestData as UserProfile
        assertEquals(testProfileQuery, userProfile.userProfile)
        assertNull(userProfile.extractedProfile)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenEmailDataReceivedWithMultipleFieldsThenStoresAllFields() = runTest {
        val extractedData = mapOf(
            "verificationCode" to "123456",
            "magicLink" to "https://example.com/verify?token=abc",
        )
        val event = EmailDataReceived(emailExtractedData = extractedData)

        val result = testee.invoke(stateForOptOut(), event)

        assertEquals(extractedData, result.nextState.emailExtractedData)
        assertEquals(2, result.nextState.emailExtractedData.size)
    }

    @Test
    fun whenEmailDataReceivedThenPreservesOtherStateFields() = runTest {
        val state = stateForOptOut(attemptId = "test-attempt")
        val event = EmailDataReceived(emailExtractedData = mapOf("verificationCode" to "abc"))

        val result = testee.invoke(state, event)

        assertEquals(0, result.nextState.currentBrokerStepIndex)
        assertEquals("test-attempt", result.nextState.attemptId)
    }

    @Test
    fun whenEmailDataReceivedThenResetsActionRetryCount() = runTest {
        val state = stateForOptOut(actionRetryCount = 3)
        val event = EmailDataReceived(emailExtractedData = mapOf("verificationCode" to "abc"))

        val result = testee.invoke(state, event)

        assertEquals(0, result.nextState.actionRetryCount)
    }

    @Test
    fun whenEmailDataReceivedWithOptOutStepThenEmitsPixel() = runTest {
        val state = stateForOptOut(actionRetryCount = 1, attemptId = "attempt-456")
        val event = EmailDataReceived(emailExtractedData = mapOf("verificationCode" to "abc"))

        testee.invoke(state, event)

        val capturedState = argumentCaptor<BrokerOptOutStageEmailGetDataReceived>()
        verify(mockPirRunStateHandler).handleState(capturedState.capture())
        assertEquals(testBroker, capturedState.firstValue.broker)
        assertEquals("get-email-data-action-1", capturedState.firstValue.actionID)
        assertEquals("attempt-456", capturedState.firstValue.attemptId)
        assertEquals(testCurrentTimeInMillis - testStageStartMs, capturedState.firstValue.durationMs)
        assertEquals(2, capturedState.firstValue.currentActionAttemptCount)
    }

    @Test
    fun whenEmailDataReceivedWithScanStepThenDoesNotEmitPixel() = runTest {
        val scanStep = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testGetEmailDataAction),
                scanType = "initial",
            ),
        )
        val state = stateWithStep(scanStep, runType = RunType.MANUAL)
        val event = EmailDataReceived(emailExtractedData = mapOf("verificationCode" to "abc"))

        testee.invoke(state, event)

        verifyNoInteractions(mockPirRunStateHandler)
    }

    @Test
    fun whenEmailDataReceivedWithEmailConfirmationStepThenDoesNotEmitPixel() = runTest {
        val emailConfirmationStep = EmailConfirmationStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testGetEmailDataAction),
                optOutType = "form",
            ),
            emailConfirmationJob = testEmailConfirmationJob,
            profileToOptOut = testExtractedProfile,
        )
        val state = stateWithStep(emailConfirmationStep, runType = RunType.EMAIL_CONFIRMATION)
        val event = EmailDataReceived(emailExtractedData = mapOf("verificationCode" to "abc"))

        testee.invoke(state, event)

        verifyNoInteractions(mockPirRunStateHandler)
    }

    private fun stateForOptOut(
        currentActionIndex: Int = 0,
        actionRetryCount: Int = 0,
        attemptId: String = "",
    ): State {
        val optOutStep = OptOutStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = List(currentActionIndex + 1) { testGetEmailDataAction },
                optOutType = "form",
            ),
            profileToOptOut = testExtractedProfile,
        )
        return State(
            runType = RunType.OPTOUT,
            brokerStepsToExecute = listOf(optOutStep),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            currentActionIndex = currentActionIndex,
            actionRetryCount = actionRetryCount,
            attemptId = attemptId,
            stageStatus = PirStageStatus(
                currentStage = PirStage.EMAIL_DATA_POLL,
                stageStartMs = testStageStartMs,
            ),
        )
    }

    private fun stateWithStep(
        step: BrokerStep,
        runType: RunType,
    ): State = State(
        runType = runType,
        brokerStepsToExecute = listOf(step),
        profileQuery = testProfileQuery,
        currentBrokerStepIndex = 0,
        currentActionIndex = 0,
        stageStatus = PirStageStatus(
            currentStage = PirStage.EMAIL_DATA_POLL,
            stageStartMs = testStageStartMs,
        ),
    )
}
