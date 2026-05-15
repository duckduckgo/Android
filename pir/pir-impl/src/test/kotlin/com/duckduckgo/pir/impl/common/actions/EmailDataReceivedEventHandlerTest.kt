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
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.EmailDataReceived
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EmailDataReceivedEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: EmailDataReceivedEventHandler

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
            currentActionIndex = 2,
            stageStatus = PirStageStatus(
                currentStage = PirStage.EMAIL_DATA_POLL,
                stageStartMs = 1000L,
            ),
        )

    @Before
    fun setUp() {
        testee = EmailDataReceivedEventHandler()
    }

    @Test
    fun whenEventIsEmailDataReceivedThenEventTypeIsCorrect() {
        assertEquals(EmailDataReceived::class, testee.event)
    }

    @Test
    fun whenEmailDataReceivedThenStoresExtractedDataInState() = runTest {
        val extractedData = mapOf("verificationCode" to "123456")
        val event = EmailDataReceived(emailExtractedData = extractedData)

        val result = testee.invoke(baseState, event)

        assertEquals(extractedData, result.nextState.emailExtractedData)
    }

    @Test
    fun whenEmailDataReceivedThenAdvancesActionIndex() = runTest {
        val event = EmailDataReceived(emailExtractedData = mapOf("verificationCode" to "abc"))

        val result = testee.invoke(baseState, event)

        assertEquals(3, result.nextState.currentActionIndex)
    }

    @Test
    fun whenEmailDataReceivedThenDispatchesExecuteBrokerStepActionWithProfileQuery() = runTest {
        val event = EmailDataReceived(emailExtractedData = mapOf("verificationCode" to "abc"))

        val result = testee.invoke(baseState, event)

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

        val result = testee.invoke(baseState, event)

        assertEquals(extractedData, result.nextState.emailExtractedData)
        assertEquals(2, result.nextState.emailExtractedData.size)
    }

    @Test
    fun whenEmailDataReceivedThenPreservesOtherStateFields() = runTest {
        val state = baseState.copy(
            currentBrokerStepIndex = 4,
            actionRetryCount = 1,
            attemptId = "test-attempt",
        )
        val event = EmailDataReceived(emailExtractedData = mapOf("verificationCode" to "abc"))

        val result = testee.invoke(state, event)

        assertEquals(4, result.nextState.currentBrokerStepIndex)
        assertEquals(1, result.nextState.actionRetryCount)
        assertEquals("test-attempt", result.nextState.attemptId)
    }
}
