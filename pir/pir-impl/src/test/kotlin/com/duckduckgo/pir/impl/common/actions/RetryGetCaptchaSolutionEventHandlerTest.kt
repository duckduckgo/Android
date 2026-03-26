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
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.RetryGetCaptchaSolution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.GetCaptchaSolution
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.PirSuccessResponse.GetCaptchaInfoResponse.ResponseData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RetryGetCaptchaSolutionEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RetryGetCaptchaSolutionEventHandler

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

    private val testResponseData =
        ResponseData(
            siteKey = "test-site-key",
            url = "https://example.com",
            type = "recaptcha",
        )

    @Before
    fun setUp() {
        testee = RetryGetCaptchaSolutionEventHandler()
    }

    @Test
    fun whenEventIsRetryGetCaptchaSolutionThenEventTypeIsCorrect() {
        assertEquals(RetryGetCaptchaSolution::class, testee.event)
    }

    @Test
    fun whenRetryGetCaptchaSolutionThenReturnsGetCaptchaSolutionSideEffect() = runTest {
        val state =
            State(
                runType = RunType.OPTOUT,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.CAPTCHA_SEND,
                    stageStartMs = 0,
                ),
            )
        val event =
            RetryGetCaptchaSolution(
                actionId = "action-1",
                responseData = testResponseData,
            )

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        assertEquals(
            GetCaptchaSolution(
                actionId = "action-1",
                responseData = testResponseData,
                isRetry = true,
            ),
            result.sideEffect,
        )
        assertNull(result.nextEvent)
    }

    @Test
    fun whenRetryGetCaptchaSolutionThenSetsIsRetryToTrue() = runTest {
        val state =
            State(
                runType = RunType.OPTOUT,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.CAPTCHA_SEND,
                    stageStartMs = 0,
                ),
            )
        val event =
            RetryGetCaptchaSolution(
                actionId = "action-1",
                responseData = testResponseData,
            )

        val result = testee.invoke(state, event)

        val sideEffect = result.sideEffect as GetCaptchaSolution
        assertTrue(sideEffect.isRetry)
    }

    @Test
    fun whenRetryGetCaptchaSolutionWithNullResponseDataThenPassesNullResponseData() = runTest {
        val state =
            State(
                runType = RunType.OPTOUT,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.CAPTCHA_SEND,
                    stageStartMs = 0,
                ),
            )
        val event =
            RetryGetCaptchaSolution(
                actionId = "action-1",
                responseData = null,
            )

        val result = testee.invoke(state, event)

        val sideEffect = result.sideEffect as GetCaptchaSolution
        assertNull(sideEffect.responseData)
        assertEquals("action-1", sideEffect.actionId)
        assertTrue(sideEffect.isRetry)
    }

    @Test
    fun whenRetryGetCaptchaSolutionThenPreservesActionId() = runTest {
        val state =
            State(
                runType = RunType.OPTOUT,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.CAPTCHA_SEND,
                    stageStartMs = 0,
                ),
            )
        val event =
            RetryGetCaptchaSolution(
                actionId = "action-captcha-456",
                responseData = testResponseData,
            )

        val result = testee.invoke(state, event)

        val sideEffect = result.sideEffect as GetCaptchaSolution
        assertEquals("action-captcha-456", sideEffect.actionId)
        assertEquals(testResponseData, sideEffect.responseData)
    }

    @Test
    fun whenRetryGetCaptchaSolutionThenStateRemainsUnchanged() = runTest {
        val state =
            State(
                runType = RunType.OPTOUT,
                brokerStepsToExecute = emptyList(),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 2,
                currentActionIndex = 4,
                actionRetryCount = 1,
                stageStatus = PirStageStatus(
                    currentStage = PirStage.CAPTCHA_SEND,
                    stageStartMs = 5000L,
                ),
            )
        val event =
            RetryGetCaptchaSolution(
                actionId = "action-1",
                responseData = testResponseData,
            )

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        assertEquals(2, result.nextState.currentBrokerStepIndex)
        assertEquals(4, result.nextState.currentActionIndex)
        assertEquals(1, result.nextState.actionRetryCount)
    }
}
