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

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingState.InProgress
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NewUserBrowserOnboardingViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val duckChat: DuckChat = mock()
    private val fakeOrchestrator = FakeOrchestrator()

    private class FakeOrchestrator : LinearOnboardingOrchestrator {
        val stateFlow = MutableStateFlow<LinearOnboardingState>(LinearOnboardingState.NotStarted)
        override val state: StateFlow<LinearOnboardingState> = stateFlow
        val events = mutableListOf<LinearOnboardingEvent>()
        override suspend fun startPlan(plan: LinearOnboardingPlan) = Unit
        override suspend fun onEvent(event: LinearOnboardingEvent) {
            // Mirror the real orchestrator contract: onEvent is a no-op before start / after terminal.
            if (stateFlow.value is InProgress) {
                events.add(event)
            }
        }
    }

    private fun onboardingActivityStep() =
        NewUserOnboardingActivityStep(
            id = "initial",
            shownEvent = null,
            transition = { LinearOnboardingTransition.Stay },
            resolveDialog = { NewUserOnboardingActivityDialog.Initial },
        )

    private fun duckAiDemoStep(prompt: String) =
        NewUserBrowserActivityStep(
            id = NewUserOnboardingStepIds.DUCK_AI_DEMO,
            shownEvent = null,
            transition = { LinearOnboardingTransition.Stay },
            resolveAction = { NewUserBrowserActivityAction.RunDuckAiOnboardingDemo(prompt) },
        )

    private fun createViewModel() = NewUserBrowserOnboardingViewModel(
        orchestrator = fakeOrchestrator,
        duckChat = duckChat,
    )

    @Test
    fun `when current step hosted by onboarding activity then hands off`() = runTest {
        val plan = LinearOnboardingPlan(id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, steps = listOf(onboardingActivityStep()))
        fakeOrchestrator.stateFlow.value =
            InProgress(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, currentPlan = plan, currentStepIndex = 0)
        val testee = createViewModel()

        testee.commands.test {
            assertEquals(NewUserBrowserOnboardingViewModel.Command.HandOffToOnboardingActivity, awaitItem())
        }
    }

    @Test
    fun `when duck ai demo step then opens duck chat`() = runTest {
        whenever(duckChat.getDuckChatUrl("hello", autoPrompt = true)).thenReturn("https://duck.ai?q=hello")
        val demoPlan = LinearOnboardingPlan(id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, steps = listOf(duckAiDemoStep("hello")))
        fakeOrchestrator.stateFlow.value =
            InProgress(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, currentPlan = demoPlan, currentStepIndex = 0)
        val testee = createViewModel()

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is NewUserBrowserOnboardingViewModel.Command.OpenDuckAiOnboardingDemo)
            assertEquals(
                "https://duck.ai?q=hello&flow=mobile-app-onboarding",
                (command as NewUserBrowserOnboardingViewModel.Command.OpenDuckAiOnboardingDemo).url,
            )
        }
    }

    @Test
    fun `when fire completed on demo step then forwards event`() = runTest {
        whenever(duckChat.getDuckChatUrl("hello", autoPrompt = true)).thenReturn("https://duck.ai?q=hello")
        val demoPlan = LinearOnboardingPlan(id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, steps = listOf(duckAiDemoStep("hello")))
        fakeOrchestrator.stateFlow.value =
            InProgress(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, currentPlan = demoPlan, currentStepIndex = 0)
        val testee = createViewModel()
        advanceUntilIdle()

        testee.onDuckAiFireCompleted()
        advanceUntilIdle()

        assertTrue(fakeOrchestrator.events.contains(NewUserOnboardingEvent.DuckAiFireCompleted))
    }

    @Test
    fun `when not started then no commands and no fire forwarding`() = runTest {
        val testee = createViewModel()
        advanceUntilIdle()

        testee.onDuckAiFireCompleted()
        advanceUntilIdle()

        assertTrue(fakeOrchestrator.events.isEmpty())
        testee.commands.test {
            expectNoEvents()
        }
    }
}
