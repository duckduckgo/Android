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
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.PreSeedCookies
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.PirStageStatus
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.SideEffect.LoadUrl
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.pixels.PirStage
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PreseedCookiesEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PreseedCookiesEventHandler

    private val testBrokerUrl = "https://test-broker.com"

    private val testBroker = Broker(
        name = "test-broker",
        fileName = "test-broker.json",
        url = testBrokerUrl,
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
        testee = PreseedCookiesEventHandler()
    }

    @Test
    fun whenEventIsPreSeedCookiesThenEventTypeIsCorrect() {
        assertEquals(PreSeedCookies::class, testee.event)
    }

    @Test
    fun whenCurrentBrokerStepIsNullThenReturnsStateUnchanged() = runTest {
        val state = State(
            runType = RunType.MANUAL,
            brokerStepsToExecute = emptyList(),
            profileQuery = testProfileQuery,
            currentBrokerStepIndex = 0,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = PreSeedCookies

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        assertNull(result.nextEvent)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenCurrentBrokerStepIndexOutOfBoundsThenReturnsStateUnchanged() = runTest {
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
            currentBrokerStepIndex = 5, // Out of bounds
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = PreSeedCookies

        val result = testee.invoke(state, event)

        assertEquals(state, result.nextState)
        assertNull(result.nextEvent)
        assertNull(result.sideEffect)
    }

    @Test
    fun whenCurrentBrokerStepExistsThenSetsPendingUrlAndPreseedingAndReturnsLoadUrlSideEffect() = runTest {
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
            preseeding = false,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = PreSeedCookies

        val result = testee.invoke(state, event)

        assertEquals(testBrokerUrl, result.nextState.pendingUrl)
        assertEquals(true, result.nextState.preseeding)
        assertEquals(LoadUrl(testBrokerUrl), result.sideEffect)
        assertNull(result.nextEvent)
    }

    @Test
    fun whenMultipleBrokerStepsThenUsesCurrentBrokerStepUrl() = runTest {
        val broker1 = testBroker.copy(name = "broker-1", url = "https://broker1.com")
        val broker2 = testBroker.copy(name = "broker-2", url = "https://broker2.com")
        val scanStep1 = ScanStep(
            broker = broker1,
            step = ScanStepActions(
                stepType = "scan",
                actions = listOf(testAction),
                scanType = "initial",
            ),
        )
        val scanStep2 = ScanStep(
            broker = broker2,
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
            currentBrokerStepIndex = 1, // Second broker
            preseeding = false,
            stageStatus = PirStageStatus(
                currentStage = PirStage.OTHER,
                stageStartMs = 0,
            ),
        )
        val event = PreSeedCookies

        val result = testee.invoke(state, event)

        assertEquals("https://broker2.com", result.nextState.pendingUrl)
        assertEquals(true, result.nextState.preseeding)
        assertEquals(LoadUrl("https://broker2.com"), result.sideEffect)
    }

    @Test
    fun whenPreseedCookiesThenPreservesOtherStateFields() = runTest {
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
            currentActionIndex = 3,
            actionRetryCount = 2,
            transactionID = "txn-123",
            attemptId = "attempt-456",
            stageStatus = PirStageStatus(
                currentStage = PirStage.FILL_FORM,
                stageStartMs = 5000L,
            ),
        )
        val event = PreSeedCookies

        val result = testee.invoke(state, event)

        // Verify other state fields are preserved
        assertEquals(RunType.SCHEDULED, result.nextState.runType)
        assertEquals(testProfileQuery, result.nextState.profileQuery)
        assertEquals(0, result.nextState.currentBrokerStepIndex)
        assertEquals(3, result.nextState.currentActionIndex)
        assertEquals(2, result.nextState.actionRetryCount)
        assertEquals("txn-123", result.nextState.transactionID)
        assertEquals("attempt-456", result.nextState.attemptId)
        assertEquals(PirStage.FILL_FORM, result.nextState.stageStatus.currentStage)
    }
}
