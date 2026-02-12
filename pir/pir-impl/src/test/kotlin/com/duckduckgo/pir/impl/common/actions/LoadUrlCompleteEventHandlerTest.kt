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
import com.duckduckgo.pir.impl.common.PirJobConstants.DBP_INITIAL_URL
import com.duckduckgo.pir.impl.common.PirJobConstants.RECOVERY_URL
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted.StepStatus.Failure
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerStep
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.LoadUrlComplete
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.PirError
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LoadUrlCompleteEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: LoadUrlCompleteEventHandler

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
        testee = LoadUrlCompleteEventHandler()
    }

    @Test
    fun whenEventIsLoadUrlCompleteThenEventTypeIsCorrect() {
        assertEquals(LoadUrlComplete::class, testee.event)
    }

    @Test
    fun whenLoadUrlCompleteAndPendingUrlIsNullThenReturnsStateUnchanged() = runTest {
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
        val event = LoadUrlComplete(url = "https://example.com")

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        assertNull(result.nextEvent)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenLoadUrlCompleteWithInitialUrlThenStartsBrokerStepExecution() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = DBP_INITIAL_URL,
                currentBrokerStepIndex = 5,
                currentActionIndex = 3,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlComplete(url = DBP_INITIAL_URL)

        val result = testee.invoke(state, event)

        assertEquals(0, result.nextState.currentBrokerStepIndex)
        assertEquals(0, result.nextState.currentActionIndex)
        assertNull(result.nextState.pendingUrl)
        assertEquals(ExecuteNextBrokerStep, result.nextEvent)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenLoadUrlCompleteWithRecoveryUrlThenCompletesBrokerStepWithFailure() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = RECOVERY_URL,
                currentBrokerStepIndex = 2,
                currentActionIndex = 4,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlComplete(url = RECOVERY_URL)

        val result = testee.invoke(state, event)

        assertNull(result.nextState.pendingUrl)
        assertEquals(2, result.nextState.currentBrokerStepIndex)
        assertEquals(4, result.nextState.currentActionIndex)
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
    fun whenLoadUrlCompleteWithNormalUrlThenProceedsToNextAction() = runTest {
        val state =
            State(
                runType = RunType.OPTOUT,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = "https://broker.com/page",
                currentBrokerStepIndex = 1,
                currentActionIndex = 2,
                actionRetryCount = 1,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.FILL_FORM,
                    stageStartMs = 1000L,
                ),
            )
        val event = LoadUrlComplete(url = "https://broker.com/page")

        val result = testee.invoke(state, event)

        assertNull(result.nextState.pendingUrl)
        assertEquals(1, result.nextState.currentBrokerStepIndex)
        assertEquals(3, result.nextState.currentActionIndex)
        assertEquals(0, result.nextState.actionRetryCount)
        assertEquals(
            ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
            result.nextEvent,
        )
        assertNull(result.sideEffect)
    }

    @Test
    fun whenLoadUrlCompleteWithRedirectedUrlThenStillProceedsToNextAction() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = "https://broker.com/original",
                currentBrokerStepIndex = 0,
                currentActionIndex = 1,
                actionRetryCount = 2,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlComplete(url = "https://broker.com/redirected")

        val result = testee.invoke(state, event)

        assertNull(result.nextState.pendingUrl)
        assertEquals(0, result.nextState.currentBrokerStepIndex)
        assertEquals(2, result.nextState.currentActionIndex)
        assertEquals(0, result.nextState.actionRetryCount)
        assertEquals(
            ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
            result.nextEvent,
        )
    }

    @Test
    fun whenLoadUrlCompleteWithInitialUrlAndNonZeroIndicesThenResetsToZero() = runTest {
        val state =
            State(
                runType = RunType.SCHEDULED,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = DBP_INITIAL_URL,
                currentBrokerStepIndex = 10,
                currentActionIndex = 5,
                actionRetryCount = 3,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlComplete(url = DBP_INITIAL_URL)

        val result = testee.invoke(state, event)

        assertEquals(0, result.nextState.currentBrokerStepIndex)
        assertEquals(0, result.nextState.currentActionIndex)
        assertEquals(3, result.nextState.actionRetryCount)
        assertNull(result.nextState.pendingUrl)
        assertEquals(ExecuteNextBrokerStep, result.nextEvent)
    }

    @Test
    fun whenLoadUrlCompleteWithNormalUrlThenResetsActionRetryCount() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = "https://example.com",
                currentActionIndex = 0,
                actionRetryCount = 5,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlComplete(url = "https://example.com")

        val result = testee.invoke(state, event)

        assertEquals(0, result.nextState.actionRetryCount)
        assertEquals(1, result.nextState.currentActionIndex)
    }

    @Test
    fun whenLoadUrlCompleteWhilePreseedingThenClearsPendingUrlAndReturnsExecuteNextBrokerStep() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = "https://broker.com",
                currentBrokerStepIndex = 0,
                currentActionIndex = 0,
                preseeding = true,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlComplete(url = "https://broker.com")

        val result = testee.invoke(state, event)

        assertNull(result.nextState.pendingUrl)
        assertEquals(ExecuteNextBrokerStep, result.nextEvent)
        assertNull(result.sideEffect)
        // Verify indices are not changed during preseeding completion
        assertEquals(0, result.nextState.currentBrokerStepIndex)
        assertEquals(0, result.nextState.currentActionIndex)
    }

    @Test
    fun whenLoadUrlCompleteWhilePreseedingAndUrlIsRedirectedThenStillClearsAndProceeds() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = "https://broker.com/original",
                currentBrokerStepIndex = 1,
                currentActionIndex = 2,
                preseeding = true,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlComplete(url = "https://broker.com/redirected")

        val result = testee.invoke(state, event)

        assertNull(result.nextState.pendingUrl)
        assertEquals(ExecuteNextBrokerStep, result.nextEvent)
        // Verify preseeding state is preserved (not modified by this handler)
        assertEquals(true, result.nextState.preseeding)
    }

    @Test
    fun whenLoadUrlCompleteNotPreseedingThenProceedsToNextAction() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                pendingUrl = "https://broker.com",
                currentBrokerStepIndex = 0,
                currentActionIndex = 0,
                preseeding = false,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = LoadUrlComplete(url = "https://broker.com")

        val result = testee.invoke(state, event)

        assertNull(result.nextState.pendingUrl)
        // When not preseeding, it should proceed to next action
        assertEquals(1, result.nextState.currentActionIndex)
        assertEquals(
            ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
            result.nextEvent,
        )
    }
}
