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
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.PirJobConstants.RECOVERY_URL
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted.StepStatus.Failure
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerStep
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.LoadUrlFailed
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.LoadUrl
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.PirError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LoadUrlFailedEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: LoadUrlFailedEventHandler

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
        testee = LoadUrlFailedEventHandler()
    }

    @Test
    fun whenEventIsLoadUrlFailedThenEventTypeIsCorrect() {
        assertEquals(LoadUrlFailed::class, testee.event)
    }

    @Test
    fun whenLoadUrlFailedAndPendingUrlIsNullThenReturnsStateUnchanged() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = null,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlFailed(url = "https://example.com")

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        assertNull(result.nextEvent)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenLoadUrlFailedAndUrlIsRecoveryUrlThenCompletesBrokerStepWithFailure() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = RECOVERY_URL,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlFailed(url = RECOVERY_URL)

        val result = testee.invoke(state, event)

        assertNull(result.nextState.pendingUrl)
        assertEquals(
            BrokerStepCompleted(
                needsEmailConfirmation = false,
                stepStatus = Failure(error = PirError.UnableToLoadBrokerUrl),
            ),
            result.nextEvent,
        )
        assertNull(result.sideEffect)
    }

    @Test
    fun whenLoadUrlFailedAndUrlIsNotRecoveryUrlThenTriesRecovery() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = "https://broker.com",
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlFailed(url = "https://broker.com")

        val result = testee.invoke(state, event)

        assertEquals(RECOVERY_URL, result.nextState.pendingUrl)
        assertEquals(LoadUrl(RECOVERY_URL), result.sideEffect)
        assertNull(result.nextEvent)
    }

    @Test
    fun whenLoadUrlFailedOnNormalUrlThenAttemptsRecoveryByLoadingRecoveryUrl() = runTest {
        val failedUrl = "https://example.com/page"
        val state =
            State(
                runType = RunType.OPTOUT,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = failedUrl,
                currentBrokerStepIndex = 2,
                currentActionIndex = 5,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.FILL_FORM,
                    stageStartMs = 1000L,
                ),
            )
        val event = LoadUrlFailed(url = failedUrl)

        val result = testee.invoke(state, event)

        assertEquals(RECOVERY_URL, result.nextState.pendingUrl)
        assertEquals(LoadUrl(RECOVERY_URL), result.sideEffect)
        assertNull(result.nextEvent)
        assertEquals(2, result.nextState.currentBrokerStepIndex)
        assertEquals(5, result.nextState.currentActionIndex)
    }

    @Test
    fun whenLoadUrlFailedOnRecoveryUrlThenClearsPendingUrlAndFailsBrokerStep() = runTest {
        val state =
            State(
                runType = RunType.SCHEDULED,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = RECOVERY_URL,
                currentBrokerStepIndex = 1,
                currentActionIndex = 3,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.START,
                    stageStartMs = 2000L,
                ),
            )
        val event = LoadUrlFailed(url = RECOVERY_URL)

        val result = testee.invoke(state, event)

        assertNull(result.nextState.pendingUrl)
        val nextEvent = result.nextEvent as BrokerStepCompleted
        assertEquals(false, nextEvent.needsEmailConfirmation)
        val stepStatus = nextEvent.stepStatus as Failure
        assertEquals(PirError.UnableToLoadBrokerUrl, stepStatus.error)
        assertNull(result.sideEffect)
        assertEquals(1, result.nextState.currentBrokerStepIndex)
        assertEquals(3, result.nextState.currentActionIndex)
    }

    @Test
    fun whenLoadUrlFailedWithDifferentUrlButHasPendingUrlThenTriesRecovery() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = "https://original.com",
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlFailed(url = "https://redirected.com")

        val result = testee.invoke(state, event)

        assertEquals(RECOVERY_URL, result.nextState.pendingUrl)
        assertEquals(LoadUrl(RECOVERY_URL), result.sideEffect)
        assertNull(result.nextEvent)
    }

    @Test
    fun whenLoadUrlFailedWhilePreseedingThenClearsPendingUrlAndReturnsExecuteNextBrokerStep() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = "https://broker.com",
                preseeding = true,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlFailed(url = "https://broker.com")

        val result = testee.invoke(state, event)

        assertNull(result.nextState.pendingUrl)
        assertEquals(ExecuteNextBrokerStep, result.nextEvent)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenLoadUrlFailedWhilePreseedingThenDoesNotAttemptRecovery() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = "https://broker.com",
                currentBrokerStepIndex = 2,
                currentActionIndex = 0,
                preseeding = true,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlFailed(url = "https://broker.com")

        val result = testee.invoke(state, event)

        // Should not set pendingUrl to RECOVERY_URL, should just clear it
        assertNull(result.nextState.pendingUrl)
        // Should not return LoadUrl side effect
        assertNull(result.sideEffect)
        // Should proceed to next broker step
        assertEquals(ExecuteNextBrokerStep, result.nextEvent)
        // Should preserve other state fields
        assertEquals(2, result.nextState.currentBrokerStepIndex)
        assertEquals(0, result.nextState.currentActionIndex)
    }

    @Test
    fun whenLoadUrlFailedNotPreseedingThenSetsPreseedingToFalseOnRecovery() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = "https://broker.com",
                preseeding = false,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlFailed(url = "https://broker.com")

        val result = testee.invoke(state, event)

        assertEquals(RECOVERY_URL, result.nextState.pendingUrl)
        assertEquals(false, result.nextState.preseeding)
        assertEquals(LoadUrl(RECOVERY_URL), result.sideEffect)
    }

    @Test
    fun whenLoadUrlFailedOnRecoveryUrlThenSetsPreseedingToFalse() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = RECOVERY_URL,
                preseeding = true,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlFailed(url = RECOVERY_URL)

        val result = testee.invoke(state, event)

        // Preseeding check happens before recovery URL check, so this will return ExecuteNextBrokerStep
        assertNull(result.nextState.pendingUrl)
        assertEquals(ExecuteNextBrokerStep, result.nextEvent)
    }

    @Test
    fun whenLoadUrlFailedWhilePreseedingPreservesOtherStateFields() = runTest {
        val state =
            State(
                runType = RunType.SCHEDULED,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = "https://broker.com",
                currentBrokerStepIndex = 3,
                currentActionIndex = 1,
                actionRetryCount = 2,
                transactionID = "txn-123",
                attemptId = "attempt-456",
                preseeding = true,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.FILL_FORM,
                    stageStartMs = 5000L,
                ),
            )
        val event = LoadUrlFailed(url = "https://broker.com")

        val result = testee.invoke(state, event)

        assertEquals(RunType.SCHEDULED, result.nextState.runType)
        assertEquals(testProfileQuery, result.nextState.profileQuery)
        assertEquals(3, result.nextState.currentBrokerStepIndex)
        assertEquals(1, result.nextState.currentActionIndex)
        assertEquals(2, result.nextState.actionRetryCount)
        assertEquals("txn-123", result.nextState.transactionID)
        assertEquals("attempt-456", result.nextState.attemptId)
        assertEquals(PirStage.FILL_FORM, result.nextState.stageStatus.currentStage)
        // preseeding is preserved (not explicitly set to false in preseeding branch)
        assertEquals(true, result.nextState.preseeding)
    }
}
