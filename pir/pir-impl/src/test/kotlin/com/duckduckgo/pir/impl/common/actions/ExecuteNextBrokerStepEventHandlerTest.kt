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
import com.duckduckgo.pir.impl.common.PirJobConstants.preSeedList
import com.duckduckgo.pir.impl.common.PirRunStateHandler
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordEmailConfirmationStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerRecordOptOutStarted
import com.duckduckgo.pir.impl.common.PirRunStateHandler.PirRunState.BrokerScanStarted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerStep
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.PreSeedCookies
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.CompleteExecution
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
import org.mockito.kotlin.whenever

class ExecuteNextBrokerStepEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: ExecuteNextBrokerStepEventHandler
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockPirRunStateHandler: PirRunStateHandler = mock()

    private val testCurrentTimeInMillis = 10000L
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

    private val testAction = BrokerAction.Navigate(
        id = "action-1",
        url = "https://example.com",
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
        testee = ExecuteNextBrokerStepEventHandler(
            mockCurrentTimeProvider,
            mockPirRunStateHandler,
        )
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(testCurrentTimeInMillis)
    }

    @Test
    fun whenEventIsExecuteNextBrokerStepThenEventTypeIsCorrect() {
        assertEquals(ExecuteNextBrokerStep::class, testee.event)
    }

    @Test
    fun whenAllBrokersExecutedThenReturnsCompleteExecutionSideEffect() = runTest {
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
            currentBrokerStepIndex = 1, // Beyond the list
            currentActionIndex = 5,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteNextBrokerStep

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        assertEquals(CompleteExecution, result.sideEffect)
        assertNull(result.nextEvent)
    }

    @Test
    fun whenCurrentBrokerStepIndexEqualsListSizeThenCompletes() = runTest {
        val scanStep1 = ScanStep(
            broker = testBroker,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            ),
        )
        val scanStep2 = ScanStep(
            broker = testBroker.copy(name = "broker-2"),
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            ),
        )
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = listOf(scanStep1, scanStep2),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 2, // Equals size
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteNextBrokerStep

        val result = testee.invoke(state, event)

        assertEquals(CompleteExecution, result.sideEffect)
    }

    @Test
    fun whenManualScanBrokerThenEmitsBrokerScanStartedPixelAndResetsState() = runTest {
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
            currentActionIndex = 5,
            actionRetryCount = 3,
            brokerStepStartTime = 1000L,
            stageStatus = PirStageStatus(
                currentStage = PirStage.VALIDATE,
                stageStartMs = 2000L,
            ),
        )
        val event = ExecuteNextBrokerStep

        val result = testee.invoke(state, event)

        // Verify pixel emission
        val capturedPixel = argumentCaptor<BrokerScanStarted>()
        verify(mockPirRunStateHandler).handleState(capturedPixel.capture())
        assertEquals(testBroker, capturedPixel.firstValue.broker)
        assertEquals(testCurrentTimeInMillis, capturedPixel.firstValue.eventTimeInMillis)

        // Verify state updates
        assertEquals(0, result.nextState.currentActionIndex)
        assertEquals(0, result.nextState.actionRetryCount)
        assertEquals(testCurrentTimeInMillis, result.nextState.brokerStepStartTime)
        assertEquals(PirStage.START, result.nextState.stageStatus.currentStage)
        assertEquals(testCurrentTimeInMillis, result.nextState.stageStatus.stageStartMs)

        // Verify next event
        val nextEvent = result.nextEvent as ExecuteBrokerStepAction
        assertEquals(testProfileQuery, (nextEvent.actionRequestData as UserProfile).userProfile)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenScheduledScanBrokerThenEmitsBrokerScanStartedPixel() = runTest {
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
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteNextBrokerStep

        testee.invoke(state, event)

        val capturedPixel = argumentCaptor<BrokerScanStarted>()
        verify(mockPirRunStateHandler).handleState(capturedPixel.capture())
        assertEquals(testBroker, capturedPixel.firstValue.broker)
        assertEquals(testCurrentTimeInMillis, capturedPixel.firstValue.eventTimeInMillis)
    }

    @Test
    fun whenOptOutBrokerThenEmitsBrokerRecordOptOutStartedPixel() = runTest {
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
            attemptId = "attempt-789",
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteNextBrokerStep

        val result = testee.invoke(state, event)

        val capturedPixel = argumentCaptor<BrokerRecordOptOutStarted>()
        verify(mockPirRunStateHandler).handleState(capturedPixel.capture())
        assertEquals(testBroker, capturedPixel.firstValue.broker)
        assertEquals(testExtractedProfile, capturedPixel.firstValue.extractedProfile)
        assertEquals("attempt-789", capturedPixel.firstValue.attemptId)

        // Verify state updates
        assertEquals(0, result.nextState.currentActionIndex)
        assertEquals(0, result.nextState.actionRetryCount)
        assertEquals(PirStage.START, result.nextState.stageStatus.currentStage)
    }

    @Test
    fun whenEmailConfirmationBrokerThenEmitsEmailConfirmationStartedPixel() = runTest {
        val emailConfirmationStep = EmailConfirmationStep(
            broker = testBroker,
            step = OptOutStepActions(
                stepType = "optout",
                actions = listOf(testAction),
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
        val event = ExecuteNextBrokerStep

        val result = testee.invoke(state, event)

        val capturedPixel = argumentCaptor<BrokerRecordEmailConfirmationStarted>()
        verify(mockPirRunStateHandler).handleState(capturedPixel.capture())
        assertEquals(testBroker, capturedPixel.firstValue.broker)
        assertEquals(456L, capturedPixel.firstValue.extractedProfileId)
        assertEquals("action-1", capturedPixel.firstValue.firstActionId)

        // Verify stage is EMAIL_CONFIRM_DECOUPLED for email confirmation
        assertEquals(PirStage.EMAIL_CONFIRM_DECOUPLED, result.nextState.stageStatus.currentStage)
    }

    @Test
    fun whenExecuteNextBrokerStepThenResetsActionIndexAndRetryCount() = runTest {
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
            currentActionIndex = 10,
            actionRetryCount = 5,
            brokerStepStartTime = 5000L,
            stageStatus = PirStageStatus(
                currentStage = PirStage.VALIDATE,
                stageStartMs = 7000L,
            ),
        )
        val event = ExecuteNextBrokerStep

        val result = testee.invoke(state, event)

        assertEquals(0, result.nextState.currentActionIndex)
        assertEquals(0, result.nextState.actionRetryCount)
        assertEquals(testCurrentTimeInMillis, result.nextState.brokerStepStartTime)
    }

    @Test
    fun whenExecuteNextBrokerStepThenPreservesOtherStateFields() = runTest {
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
            transactionID = "txn-123",
            attemptId = "attempt-456",
            pendingUrl = "https://example.com",
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteNextBrokerStep

        val result = testee.invoke(state, event)

        assertEquals(RunType.SCHEDULED, result.nextState.runType)
        assertEquals(testProfileQuery, result.nextState.profileQuery)
        assertEquals("txn-123", result.nextState.transactionID)
        assertEquals("attempt-456", result.nextState.attemptId)
        assertEquals("https://example.com", result.nextState.pendingUrl)
    }

    @Test
    fun whenExecuteNextBrokerStepThenReturnsExecuteBrokerStepActionEvent() = runTest {
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
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteNextBrokerStep

        val result = testee.invoke(state, event)

        val nextEvent = result.nextEvent as ExecuteBrokerStepAction
        val requestData = nextEvent.actionRequestData as UserProfile
        assertEquals(testProfileQuery, requestData.userProfile)
        assertNull(requestData.extractedProfile)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenBrokerInPreSeedListAndNotPreseedingThenReturnsPreSeedCookiesEvent() = runTest {
        val preseedBrokerName = preSeedList.first()
        val preseedBroker = testBroker.copy(name = preseedBrokerName)
        val scanStep = ScanStep(
            broker = preseedBroker,
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
            preseeding = false,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteNextBrokerStep

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        assertEquals(PreSeedCookies, result.nextEvent)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenBrokerInPreSeedListAndAlreadyPreseedingThenProceedsNormally() = runTest {
        val preseedBrokerName = preSeedList.first()
        val preseedBroker = testBroker.copy(name = preseedBrokerName)
        val scanStep = ScanStep(
            broker = preseedBroker,
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
            preseeding = true,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteNextBrokerStep

        val result = testee.invoke(state, event)

        // Verify it proceeds normally with ExecuteBrokerStepAction
        val nextEvent = result.nextEvent as ExecuteBrokerStepAction
        val requestData = nextEvent.actionRequestData as UserProfile
        assertEquals(testProfileQuery, requestData.userProfile)
        assertNull(result.sideEffect)
        // Verify preseeding is set to false after proceeding
        assertEquals(false, result.nextState.preseeding)
    }

    @Test
    fun whenBrokerNotInPreSeedListThenProceedsNormally() = runTest {
        val nonPreseedBroker = testBroker.copy(name = "NonPreseedBroker")
        val scanStep = ScanStep(
            broker = nonPreseedBroker,
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
            preseeding = false,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteNextBrokerStep

        val result = testee.invoke(state, event)

        // Verify it proceeds normally with ExecuteBrokerStepAction
        val nextEvent = result.nextEvent as ExecuteBrokerStepAction
        val requestData = nextEvent.actionRequestData as UserProfile
        assertEquals(testProfileQuery, requestData.userProfile)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenExecuteNextBrokerStepThenSetsPreseedingToFalse() = runTest {
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
            preseeding = true,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = ExecuteNextBrokerStep

        val result = testee.invoke(state, event)

        assertEquals(false, result.nextState.preseeding)
    }
}
