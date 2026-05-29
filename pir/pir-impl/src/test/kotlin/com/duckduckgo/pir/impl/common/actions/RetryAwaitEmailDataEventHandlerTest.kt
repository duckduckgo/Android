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
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.RetryAwaitEmailData
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.AwaitEmailData
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RetryAwaitEmailDataEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RetryAwaitEmailDataEventHandler

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

    private val baseState =
        State(
            runType = RunType.OPTOUT,
            brokerStepsToExecute = emptyList(),
            profileQuery = testProfileQuery,
            stageStatus = PirStageStatus(
                currentStage = PirStage.EMAIL_DATA_POLL,
                stageStartMs = 0,
            ),
        )

    private val baseEvent =
        RetryAwaitEmailData(
            actionId = "get-email-data-1",
            brokerName = "test-broker",
            emailAddress = "test@example.com",
            attemptId = "attempt-1",
            extractFields = listOf("verificationCode"),
            pollingIntervalSeconds = 5,
            maxTimeoutSeconds = 60,
            attempt = 0,
        )

    @Before
    fun setUp() {
        testee = RetryAwaitEmailDataEventHandler()
    }

    @Test
    fun whenEventIsRetryAwaitEmailDataThenEventTypeIsCorrect() {
        assertEquals(RetryAwaitEmailData::class, testee.event)
    }

    @Test
    fun whenRetryAwaitEmailDataThenReturnsAwaitEmailDataSideEffectWithIncrementedAttempt() = runTest {
        val result = testee.invoke(baseState, baseEvent)

        assertEquals(baseState, result.nextState)
        assertEquals(
            AwaitEmailData(
                actionId = "get-email-data-1",
                brokerName = "test-broker",
                emailAddress = "test@example.com",
                attemptId = "attempt-1",
                extractFields = listOf("verificationCode"),
                pollingIntervalSeconds = 5,
                maxTimeoutSeconds = 60,
                attempt = 1,
            ),
            result.sideEffect,
        )
        assertNull(result.nextEvent)
    }

    @Test
    fun whenRetryAwaitEmailDataWithHigherAttemptThenIncrementsAttempt() = runTest {
        val event = baseEvent.copy(attempt = 5)

        val result = testee.invoke(baseState, event)

        val sideEffect = result.sideEffect as AwaitEmailData
        assertEquals(6, sideEffect.attempt)
    }

    @Test
    fun whenRetryAwaitEmailDataThenPreservesAllOtherFields() = runTest {
        val event = baseEvent.copy(
            actionId = "custom-action-id",
            brokerName = "another-broker",
            emailAddress = "another@example.com",
            attemptId = "another-attempt",
            extractFields = listOf("verificationCode", "magicLink"),
            pollingIntervalSeconds = 10,
            maxTimeoutSeconds = 120,
            attempt = 3,
        )

        val result = testee.invoke(baseState, event)

        val sideEffect = result.sideEffect as AwaitEmailData
        assertEquals("custom-action-id", sideEffect.actionId)
        assertEquals("another-broker", sideEffect.brokerName)
        assertEquals("another@example.com", sideEffect.emailAddress)
        assertEquals("another-attempt", sideEffect.attemptId)
        assertEquals(listOf("verificationCode", "magicLink"), sideEffect.extractFields)
        assertEquals(10, sideEffect.pollingIntervalSeconds)
        assertEquals(120, sideEffect.maxTimeoutSeconds)
        assertEquals(4, sideEffect.attempt)
    }

    @Test
    fun whenRetryAwaitEmailDataThenStateRemainsUnchanged() = runTest {
        val state = baseState.copy(
            currentBrokerStepIndex = 3,
            currentActionIndex = 5,
            actionRetryCount = 2,
        )

        val result = testee.invoke(state, baseEvent)

        assertEquals(state, result.nextState)
        assertEquals(3, result.nextState.currentBrokerStepIndex)
        assertEquals(5, result.nextState.currentActionIndex)
        assertEquals(2, result.nextState.actionRetryCount)
    }
}
