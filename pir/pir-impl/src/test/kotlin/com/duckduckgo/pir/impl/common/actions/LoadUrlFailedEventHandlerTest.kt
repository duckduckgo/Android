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
}
