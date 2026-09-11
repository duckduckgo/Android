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

package com.duckduckgo.subscriptions.impl.onboarding

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingState.InProgress
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.SKIPPED
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingPlanProvider.Companion.SUBSCRIPTION_ONBOARDING_PLAN_ID
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStepStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class SubscriptionOnboardingViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val orchestrator = FakeOrchestrator()
    private val stepStore = SubscriptionOnboardingStepStore(FakeSharedPreferencesProvider())
    private val controller = RealSubscriptionOnboardingController()
    private val planProvider = SubscriptionOnboardingPlanProvider(emptyPluginPoint(), stepStore)

    private class FakeOrchestrator : LinearOnboardingOrchestrator {
        val stateFlow = MutableStateFlow<LinearOnboardingState>(LinearOnboardingState.NotStarted)
        override val state: StateFlow<LinearOnboardingState> = stateFlow
        val events = mutableListOf<LinearOnboardingEvent>()
        override suspend fun startPlan(plan: LinearOnboardingPlan) = Unit
        override suspend fun onEvent(event: LinearOnboardingEvent) {
            if (stateFlow.value is InProgress) events.add(event)
        }
    }

    private fun createViewModel() = SubscriptionOnboardingViewModel(orchestrator, planProvider, stepStore, controller)

    @Test
    fun whenInProgressOnActivityStepThenShowsStepWithCanGoBack() = runTest {
        val stepPlugin: SubscriptionOnboardingStepPlugin = mock()
        orchestrator.stateFlow.value = inProgressState(canGoBack = true, stepPlugin = stepPlugin)
        val testee = createViewModel()
        testee.start()

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is SubscriptionOnboardingViewModel.Command.ShowStep)
            command as SubscriptionOnboardingViewModel.Command.ShowStep
            assertEquals(stepPlugin, command.stepPlugin)
            assertTrue(command.canGoBack)
        }
    }

    @Test
    fun whenCompletedThenFinishesToSettings() = runTest {
        orchestrator.stateFlow.value = LinearOnboardingState.Completed(rootPlanId = SUBSCRIPTION_ONBOARDING_PLAN_ID)
        val testee = createViewModel()
        testee.start()

        testee.commands.test {
            assertEquals(SubscriptionOnboardingViewModel.Command.FinishToSettings, awaitItem())
        }
    }

    @Test
    fun whenSkippedThenFinishesToSettings() = runTest {
        orchestrator.stateFlow.value = LinearOnboardingState.Skipped(rootPlanId = SUBSCRIPTION_ONBOARDING_PLAN_ID)
        val testee = createViewModel()
        testee.start()

        testee.commands.test {
            assertEquals(SubscriptionOnboardingViewModel.Command.FinishToSettings, awaitItem())
        }
    }

    @Test
    fun whenStepFinishedCompletedThenPersistsAndForwards() = runTest {
        orchestrator.stateFlow.value = inProgressState()
        val testee = createViewModel()
        testee.start()
        advanceUntilIdle()

        controller.onStepFinished("welcome", COMPLETED)
        advanceUntilIdle()

        assertTrue(stepStore.isCompleted("welcome"))
        assertTrue(orchestrator.events.contains(SubscriptionOnboardingEvent.StepFinished("welcome", COMPLETED)))
    }

    @Test
    fun whenStepFinishedSkippedThenForwardsWithoutPersisting() = runTest {
        orchestrator.stateFlow.value = inProgressState()
        val testee = createViewModel()
        testee.start()
        advanceUntilIdle()

        controller.onStepFinished("welcome", SKIPPED)
        advanceUntilIdle()

        assertFalse(stepStore.isCompleted("welcome"))
        assertTrue(orchestrator.events.contains(SubscriptionOnboardingEvent.StepFinished("welcome", SKIPPED)))
    }

    @Test
    fun whenBackAndCanGoBackThenForwardsBackPressed() = runTest {
        orchestrator.stateFlow.value = inProgressState(canGoBack = true)
        val testee = createViewModel()
        testee.start()
        advanceUntilIdle()

        controller.onBack()
        advanceUntilIdle()

        assertTrue(orchestrator.events.contains(SubscriptionOnboardingEvent.BackPressed))
    }

    @Test
    fun whenBackOnFirstStepThenFinishesToSettings() = runTest {
        val testee = createViewModel()
        testee.start()
        advanceUntilIdle()

        testee.commands.test {
            controller.onBack()
            assertEquals(SubscriptionOnboardingViewModel.Command.FinishToSettings, awaitItem())
        }
    }

    @Test
    fun whenExitThenFinishes() = runTest {
        val testee = createViewModel()
        testee.start()
        advanceUntilIdle()

        testee.commands.test {
            controller.exitOnboarding()
            assertEquals(SubscriptionOnboardingViewModel.Command.Finish, awaitItem())
        }
    }

    private fun inProgressState(
        canGoBack: Boolean = false,
        stepPlugin: SubscriptionOnboardingStepPlugin = mock(),
    ): InProgress {
        val plan = LinearOnboardingPlan(
            id = SUBSCRIPTION_ONBOARDING_PLAN_ID,
            steps = listOf(
                SubscriptionOnboardingActivityStep(
                    id = "welcome",
                    transition = { LinearOnboardingTransition.Stay },
                    stepPlugin = stepPlugin,
                ),
            ),
        )
        return InProgress(
            rootPlanId = SUBSCRIPTION_ONBOARDING_PLAN_ID,
            currentPlan = plan,
            currentStepIndex = 0,
            canGoBack = canGoBack,
        )
    }

    private fun emptyPluginPoint() = object : PluginPoint<SubscriptionOnboardingStepPlugin> {
        override fun getPlugins(): Collection<SubscriptionOnboardingStepPlugin> = emptyList()
    }
}
