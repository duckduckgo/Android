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

package com.duckduckgo.app.onboarding.orchestrator

import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.OnboardingSkipper
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingState.Completed
import com.duckduckgo.onboarding.api.LinearOnboardingState.InProgress
import com.duckduckgo.onboarding.api.LinearOnboardingState.NotStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NewUserOnboardingPlanBootstrapperTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val planProvider: NewUserOnboardingPlanProvider = mock()
    private val userStageStore: UserStageStore = mock()
    private val onboardingSkipper: OnboardingSkipper = mock()
    private val fakeOrchestrator = FakeOrchestrator()

    private class FakeOrchestrator : LinearOnboardingOrchestrator {
        val stateFlow = MutableStateFlow<LinearOnboardingState>(NotStarted)
        override val state: StateFlow<LinearOnboardingState> = stateFlow
        var startedPlan: LinearOnboardingPlan? = null

        // Real orchestrator lands on InProgress for a well-formed plan; a misconfigured plan with no
        // eligible steps leaves it in a non-InProgress state. Toggle to exercise both branches.
        var startResultsInProgress = true

        override suspend fun startPlan(plan: LinearOnboardingPlan) {
            startedPlan = plan
            stateFlow.value = if (startResultsInProgress) {
                InProgress(rootPlanId = plan.id, currentPlan = plan, currentStepIndex = 0)
            } else {
                Completed(rootPlanId = plan.id)
            }
        }
        override suspend fun onEvent(event: LinearOnboardingEvent) = Unit
    }

    private lateinit var testee: NewUserOnboardingPlanBootstrapper

    @Before
    fun setup() {
        runBlocking { whenever(planProvider.buildRootPlan(any(), any())).thenReturn(LinearOnboardingPlan(id = "test_plan", steps = emptyList())) }
        testee = NewUserOnboardingPlanBootstrapper(
            orchestrator = fakeOrchestrator,
            planProvider = planProvider,
            userStageStore = userStageStore,
            onboardingSkipper = onboardingSkipper,
        )
    }

    @Test
    fun `when plan starts in progress then returns the InProgress state`() = runTest {
        fakeOrchestrator.startResultsInProgress = true

        val result = testee.startNewUserOnboardingPlan()

        assertTrue(result is InProgress)
        assertTrue(fakeOrchestrator.startedPlan != null)
    }

    @Test
    fun `when plan does not start in progress then throws`() = runTest {
        // A misconfigured plan with no eligible steps leaves the orchestrator in a non-InProgress state.
        fakeOrchestrator.startResultsInProgress = false

        val exception = runCatching { testee.startNewUserOnboardingPlan() }.exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
    }

    @Test
    fun `when terminal callbacks supplied then they write to stage store and skipper`() = runTest {
        val captor = argumentCaptor<suspend () -> Unit>()
        whenever(planProvider.buildRootPlan(captor.capture(), captor.capture()))
            .thenReturn(LinearOnboardingPlan(id = "test_plan", steps = emptyList()))

        testee.startNewUserOnboardingPlan()

        captor.firstValue.invoke()
        captor.secondValue.invoke()
        verify(userStageStore).stageCompleted(AppStage.NEW)
        verify(onboardingSkipper).markOnboardingAsCompleted()
    }
}
