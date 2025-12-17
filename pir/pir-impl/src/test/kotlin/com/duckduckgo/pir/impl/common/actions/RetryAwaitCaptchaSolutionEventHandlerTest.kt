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
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.RetryAwaitCaptchaSolution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.AwaitCaptchaSolution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RetryAwaitCaptchaSolutionEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RetryAwaitCaptchaSolutionEventHandler

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
        testee = RetryAwaitCaptchaSolutionEventHandler()
    }

    @Test
    fun whenEventIsRetryAwaitCaptchaSolutionThenEventTypeIsCorrect() {
        assertEquals(RetryAwaitCaptchaSolution::class, testee.event)
    }

    @Test
    fun whenRetryAwaitCaptchaSolutionThenReturnsAwaitCaptchaSolutionSideEffect() = runTest {
        val state =
            State(
                runType = RunType.OPTOUT,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.CAPTCHA_SOLVE,
                    stageStartMs = 0,
                ),
            )
        val event =
            RetryAwaitCaptchaSolution(
                actionId = "action-1",
                brokerName = "test-broker",
                transactionID = "transaction-123",
                attempt = 0,
            )

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        assertEquals(
            AwaitCaptchaSolution(
                actionId = "action-1",
                brokerName = "test-broker",
                transactionID = "transaction-123",
                attempt = 1,
            ),
            result.sideEffect,
        )
        assertNull(result.nextEvent)
    }

    @Test
    fun whenRetryAwaitCaptchaSolutionWithHigherAttemptThenIncrementsAttempt() = runTest {
        val state =
            State(
                runType = RunType.OPTOUT,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.CAPTCHA_SOLVE,
                    stageStartMs = 0,
                ),
            )
        val event =
            RetryAwaitCaptchaSolution(
                actionId = "action-1",
                brokerName = "test-broker",
                transactionID = "transaction-123",
                attempt = 5,
            )

        val result = testee.invoke(state, event)

        val sideEffect = result.sideEffect as AwaitCaptchaSolution
        assertEquals(6, sideEffect.attempt)
    }

    @Test
    fun whenRetryAwaitCaptchaSolutionThenPreservesActionIdAndBrokerName() = runTest {
        val state =
            State(
                runType = RunType.OPTOUT,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.CAPTCHA_SOLVE,
                    stageStartMs = 0,
                ),
            )
        val event =
            RetryAwaitCaptchaSolution(
                actionId = "action-captcha-123",
                brokerName = "some-broker",
                transactionID = "txn-456",
                attempt = 2,
            )

        val result = testee.invoke(state, event)

        val sideEffect = result.sideEffect as AwaitCaptchaSolution
        assertEquals("action-captcha-123", sideEffect.actionId)
        assertEquals("some-broker", sideEffect.brokerName)
        assertEquals("txn-456", sideEffect.transactionID)
        assertEquals(3, sideEffect.attempt)
    }

    @Test
    fun whenRetryAwaitCaptchaSolutionThenStateRemainsUnchanged() = runTest {
        val state =
            State(
                runType = RunType.OPTOUT,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 3,
                currentActionIndex = 5,
                actionRetryCount = 2,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.CAPTCHA_SOLVE,
                    stageStartMs = 1000L,
                ),
            )
        val event =
            RetryAwaitCaptchaSolution(
                actionId = "action-1",
                brokerName = "test-broker",
                transactionID = "transaction-123",
                attempt = 0,
            )

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        assertEquals(3, result.nextState.currentBrokerStepIndex)
        assertEquals(5, result.nextState.currentActionIndex)
        assertEquals(2, result.nextState.actionRetryCount)
    }
}
