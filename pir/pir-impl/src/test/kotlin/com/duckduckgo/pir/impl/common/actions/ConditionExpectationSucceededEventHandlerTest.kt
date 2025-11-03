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
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.EmailConfirmationStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.OptOutStep
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStep.ScanStep
import com.duckduckgo.pir.impl.common.PirJob.RunType
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ConditionExpectationSucceeded
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.EmailData
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.JobAttemptData
import com.duckduckgo.pir.impl.models.scheduling.JobRecord.EmailConfirmationJobRecord.LinkFetchData
import com.duckduckgo.pir.impl.scripts.models.BrokerAction
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ConditionExpectationSucceededEventHandlerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: ConditionExpectationSucceededEventHandler

    // Test data
    private val testBrokerName = "test-broker"
    private val testProfileQueryId = 123L
    private val testCurrentActionIndex = 1

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

    private val existingAction1 =
        BrokerAction.Navigate(
            id = "action-1",
            url = "https://example.com",
        )

    private val existingAction2 =
        BrokerAction.Click(
            id = "action-2",
            elements = emptyList(),
            selector = null,
        )

    private val existingAction3 =
        BrokerAction.Click(
            id = "action-3",
            elements = emptyList(),
            selector = null,
        )

    private val conditionAction1 =
        BrokerAction.FillForm(
            id = "condition-action-1",
            elements = emptyList(),
            selector = "form-selector",
        )

    private val conditionAction2 =
        BrokerAction.Click(
            id = "condition-action-2",
            elements = emptyList(),
            selector = null,
        )

    @Before
    fun setUp() {
        testee = ConditionExpectationSucceededEventHandler()
    }

    @Test
    fun whenEventIsConditionExpectationSucceededThenEventTypeIsCorrect() {
        assertEquals(ConditionExpectationSucceeded::class, testee.event)
    }

    @Test
    fun whenConditionActionsWithScanStepThenInsertsActionsAndReturnsNextEvent() = runTest {
        val scanStep =
            ScanStep(
                brokerName = testBrokerName,
                stepType = "scan",
                actions = listOf(existingAction1, existingAction2, existingAction3),
                scanType = "initial",
            )
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = listOf(scanStep),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = testCurrentActionIndex,
            )
        val conditionActions = listOf(conditionAction1, conditionAction2)
        val event = ConditionExpectationSucceeded(conditionActions = conditionActions)

        val result = testee.invoke(state, event)

        // Verify actions are inserted at currentActionIndex + 1 (after action-2)
        val updatedScanStep = result.nextState.brokerStepsToExecute[0] as ScanStep
        val expectedActions =
            listOf(
                existingAction1, // action-1
                existingAction2, // action-2 (current action)
                conditionAction1, // inserted action
                conditionAction2, // inserted action
                existingAction3, // action-3
            )
        assertEquals(expectedActions, updatedScanStep.actions)

        // Verify state is updated correctly
        assertEquals(testCurrentActionIndex + 1, result.nextState.currentActionIndex)
        assertEquals(
            ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
            result.nextEvent,
        )
        assertNull(result.sideEffect)
    }

    @Test
    fun whenConditionActionsWithOptOutStepThenInsertsActionsAndReturnsNextEvent() = runTest {
        val optOutStep =
            OptOutStep(
                brokerName = testBrokerName,
                stepType = "optout",
                actions = listOf(existingAction1, existingAction2),
                optOutType = "form",
                profileToOptOut = testExtractedProfile,
            )
        val state =
            State(
                runType = RunType.OPTOUT,
                brokerStepsToExecute = listOf(optOutStep),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = testCurrentActionIndex,
            )
        val conditionActions = listOf(conditionAction1)
        val event = ConditionExpectationSucceeded(conditionActions = conditionActions)

        val result = testee.invoke(state, event)

        val updatedOptOutStep = result.nextState.brokerStepsToExecute[0] as OptOutStep
        val expectedActions =
            listOf(
                existingAction1,
                existingAction2,
                conditionAction1,
            )
        assertEquals(expectedActions, updatedOptOutStep.actions)

        assertEquals(testCurrentActionIndex + 1, result.nextState.currentActionIndex)
        assertEquals(
            ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
            result.nextEvent,
        )
    }

    @Test
    fun whenConditionActionsWithEmailConfirmationStepThenInsertsActionsAndReturnsNextEvent() =
        runTest {
            val emailConfirmationStep =
                EmailConfirmationStep(
                    brokerName = testBrokerName,
                    stepType = "emailConfirmation",
                    actions = listOf(existingAction1, existingAction2),
                    emailConfirmationJob = testEmailConfirmationJob,
                    profileToOptOut = testExtractedProfile,
                )
            val state =
                State(
                    runType = RunType.EMAIL_CONFIRMATION,
                    brokerStepsToExecute = listOf(emailConfirmationStep),
                    profileQuery = testProfileQuery,
                    currentBrokerStepIndex = 0,
                    currentActionIndex = testCurrentActionIndex,
                )
            val conditionActions = listOf(conditionAction1)
            val event = ConditionExpectationSucceeded(conditionActions = conditionActions)

            val result = testee.invoke(state, event)

            val updatedEmailConfirmationStep =
                result.nextState.brokerStepsToExecute[0] as EmailConfirmationStep
            val expectedActions =
                listOf(
                    existingAction1,
                    existingAction2,
                    conditionAction1,
                )
            assertEquals(expectedActions, updatedEmailConfirmationStep.actions)

            assertEquals(testCurrentActionIndex + 1, result.nextState.currentActionIndex)
            assertEquals(
                ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
                result.nextEvent,
            )
        }

    @Test
    fun whenConditionActionsInsertedAtBeginningThenActionsAreInCorrectOrder() = runTest {
        val scanStep =
            ScanStep(
                brokerName = testBrokerName,
                stepType = "scan",
                actions = listOf(existingAction1, existingAction2),
                scanType = "initial",
            )
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = listOf(scanStep),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = 0, // At the beginning
            )
        val conditionActions = listOf(conditionAction1)
        val event = ConditionExpectationSucceeded(conditionActions = conditionActions)

        val result = testee.invoke(state, event)

        val updatedScanStep = result.nextState.brokerStepsToExecute[0] as ScanStep
        val expectedActions =
            listOf(
                existingAction1,
                conditionAction1, // Inserted after index 0
                existingAction2,
            )
        assertEquals(expectedActions, updatedScanStep.actions)
        assertEquals(1, result.nextState.currentActionIndex)
    }

    @Test
    fun whenConditionActionsInsertedAtEndThenActionsAreAppended() = runTest {
        val scanStep =
            ScanStep(
                brokerName = testBrokerName,
                stepType = "scan",
                actions = listOf(existingAction1, existingAction2),
                scanType = "initial",
            )
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = listOf(scanStep),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = 1, // At the last action
            )
        val conditionActions = listOf(conditionAction1, conditionAction2)
        val event = ConditionExpectationSucceeded(conditionActions = conditionActions)

        val result = testee.invoke(state, event)

        val updatedScanStep = result.nextState.brokerStepsToExecute[0] as ScanStep
        val expectedActions =
            listOf(
                existingAction1,
                existingAction2,
                conditionAction1, // Inserted at end
                conditionAction2, // Inserted at end
            )
        assertEquals(expectedActions, updatedScanStep.actions)
        assertEquals(2, result.nextState.currentActionIndex)
    }

    @Test
    fun whenMultipleBrokerStepsThenOnlyCurrentStepIsUpdated() = runTest {
        val scanStep1 =
            ScanStep(
                brokerName = testBrokerName,
                stepType = "scan",
                actions = listOf(existingAction1),
                scanType = "initial",
            )
        val scanStep2 =
            ScanStep(
                brokerName = "$testBrokerName-2",
                stepType = "scan",
                actions = listOf(existingAction2),
                scanType = "initial",
            )
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = listOf(scanStep1, scanStep2),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = 0,
            )
        val conditionActions = listOf(conditionAction1)
        val event = ConditionExpectationSucceeded(conditionActions = conditionActions)

        val result = testee.invoke(state, event)

        // Only the first step should be updated
        val updatedScanStep1 = result.nextState.brokerStepsToExecute[0] as ScanStep
        val unchangedScanStep2 = result.nextState.brokerStepsToExecute[1] as ScanStep

        assertEquals(2, updatedScanStep1.actions.size)
        assertEquals(listOf(existingAction1, conditionAction1), updatedScanStep1.actions)

        assertEquals(1, unchangedScanStep2.actions.size)
        assertEquals(listOf(existingAction2), unchangedScanStep2.actions)
    }

    @Test
    fun whenEmptyConditionActionsListThenOnlyActionIndexIsIncremented() = runTest {
        val scanStep =
            ScanStep(
                brokerName = testBrokerName,
                stepType = "scan",
                actions = listOf(existingAction1, existingAction2),
                scanType = "initial",
            )
        val state =
            State(
                runType = RunType.MANUAL,
                brokerStepsToExecute = listOf(scanStep),
                profileQuery = testProfileQuery,
                currentBrokerStepIndex = 0,
                currentActionIndex = testCurrentActionIndex,
            )
        val event = ConditionExpectationSucceeded(conditionActions = emptyList())

        val result = testee.invoke(state, event)

        // Actions should remain unchanged
        val updatedScanStep = result.nextState.brokerStepsToExecute[0] as ScanStep
        assertEquals(listOf(existingAction1, existingAction2), updatedScanStep.actions)

        // Action index should still increment
        assertEquals(testCurrentActionIndex + 1, result.nextState.currentActionIndex)
        assertEquals(
            ExecuteBrokerStepAction(UserProfile(userProfile = testProfileQuery)),
            result.nextEvent,
        )
    }
}
