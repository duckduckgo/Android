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
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.Started
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.LoadUrl
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StartedEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: StartedEventHandler

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
        testee = StartedEventHandler()
    }

    @Test
    fun whenEventIsStartedThenEventTypeIsCorrect() {
        assertEquals(Started::class, testee.event)
    }

    @Test
    fun whenStartedEventThenSetsPendingUrlToInitialUrl() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = Started

        val result = testee.invoke(state, event)

        assertEquals(DBP_INITIAL_URL, result.nextState.pendingUrl)
    }

    @Test
    fun whenStartedEventThenGeneratesAttemptId() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                attemptId = "",
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = Started

        val result = testee.invoke(state, event)

        assertNotEquals("", result.nextState.attemptId)
    }

    @Test
    fun whenStartedEventThenReturnsLoadUrlSideEffect() = runTest {
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = Started

        val result = testee.invoke(state, event)

        assertEquals(LoadUrl(url = DBP_INITIAL_URL), result.sideEffect)
        assertNull(result.nextEvent)
    }

    @Test
    fun whenStartedEventThenPreservesOtherStateFields() = runTest {
        val state =
            State(
                runType = RunType.SCHEDULED,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 5,
                currentActionIndex = 3,
                brokerStepStartTime = 1000L,
                transactionID = "test-transaction-id",
                actionRetryCount = 2,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.OTHER,
                    stageStartMs = 0,
                ),
            )
        val event = Started

        val result = testee.invoke(state, event)

        assertEquals(RunType.SCHEDULED, result.nextState.runType)
        assertEquals(5, result.nextState.currentBrokerStepIndex)
        assertEquals(3, result.nextState.currentActionIndex)
        assertEquals(1000L, result.nextState.brokerStepStartTime)
        assertEquals("test-transaction-id", result.nextState.transactionID)
        assertEquals(2, result.nextState.actionRetryCount)
    }
}
