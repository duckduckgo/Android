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

package com.duckduckgo.onboarding.impl

import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingState.Completed
import com.duckduckgo.onboarding.api.LinearOnboardingState.InProgress
import com.duckduckgo.onboarding.api.LinearOnboardingState.NotStarted
import com.duckduckgo.onboarding.api.LinearOnboardingState.Skipped
import com.duckduckgo.onboarding.api.forPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the [forPlan] extension on [Flow<LinearOnboardingState>].
 *
 * The key property under test: because the source is a [StateFlow], a late subscriber
 * collecting [forPlan] already receives the current terminal state immediately on subscription
 * (StateFlow replay-on-subscribe semantics).
 */
class LinearOnboardingStateForPlanTest {

    // --- forPlan scoping ---

    @Test
    fun `forPlan emits matching InProgress immediately on subscription`() = runTest {
        val planId = "plan_a"
        // No steps needed; we are testing the extension directly, not the orchestrator.
        val stateFlow = MutableStateFlow<LinearOnboardingState>(
            InProgress(rootPlanId = planId, currentPlan = planStub(planId), currentStepIndex = 0),
        )

        val emitted = stateFlow.forPlan(planId).first()

        assertEquals(planId, emitted.rootPlanId)
    }

    @Test
    fun `forPlan emits Completed immediately for a late subscriber when state is already terminal`() = runTest {
        val planId = "plan_a"
        // Arrange: state is already Completed before any subscriber arrives.
        val stateFlow = MutableStateFlow<LinearOnboardingState>(Completed(rootPlanId = planId))

        // Act: subscribe after the fact.
        val emitted = stateFlow.forPlan(planId).first()

        // Assert: the late subscriber still receives the current Completed state.
        assertEquals(Completed(rootPlanId = planId), emitted)
        assertEquals(planId, emitted.rootPlanId)
    }

    @Test
    fun `forPlan emits Skipped immediately for a late subscriber when state is already terminal`() = runTest {
        val planId = "plan_a"
        val stateFlow = MutableStateFlow<LinearOnboardingState>(Skipped(rootPlanId = planId))

        val emitted = stateFlow.forPlan(planId).first()

        assertEquals(Skipped(rootPlanId = planId), emitted)
        assertEquals(planId, emitted.rootPlanId)
    }

    @Test
    fun `forPlan does not emit for a different plan id when state is Completed`() = runTest {
        val stateFlow = MutableStateFlow<LinearOnboardingState>(Completed(rootPlanId = "plan_a"))

        // forPlan("plan_b") should not emit the Completed(rootPlanId = "plan_a") state.
        val emitted = withTimeoutOrNull(100) {
            stateFlow.forPlan("plan_b").first()
        }

        assertNull("forPlan(\"plan_b\") should not emit Completed for \"plan_a\"", emitted)
    }

    @Test
    fun `forPlan does not emit NotStarted`() = runTest {
        val stateFlow = MutableStateFlow<LinearOnboardingState>(NotStarted)

        val emitted = withTimeoutOrNull(100) {
            stateFlow.forPlan("any_plan").first()
        }

        assertNull("forPlan should not emit NotStarted", emitted)
    }

    @Test
    fun `forPlan filters out states from a different plan id`() = runTest {
        val planA = "plan_a"
        val planB = "plan_b"
        // Arrange: state is planB's terminal — forPlan(planA) should yield nothing.
        val stateFlow = MutableStateFlow<LinearOnboardingState>(Completed(rootPlanId = planB))

        val emitted = withTimeoutOrNull(100) {
            stateFlow.forPlan(planA).first()
        }

        assertNull("forPlan(planA) must not emit a state whose rootPlanId is planB", emitted)
    }

    @Test
    fun `forPlan emits planA Completed even when state previously held planB Completed`() = runTest {
        val planA = "plan_a"
        val planB = "plan_b"
        val stateFlow = MutableStateFlow<LinearOnboardingState>(Completed(rootPlanId = planB))

        // Transition to planA's terminal state and then subscribe.
        stateFlow.value = Completed(rootPlanId = planA)

        val emitted = stateFlow.forPlan(planA).first()

        assertEquals(Completed(rootPlanId = planA), emitted)
    }

    // Minimal plan stub — the forPlan extension only inspects rootPlanId on LinearOnboardingState.Started,
    // so the plan itself is irrelevant here; we just need a valid LinearOnboardingPlan.
    private fun planStub(id: String) = com.duckduckgo.onboarding.api.LinearOnboardingPlan(
        id = id,
        steps = emptyList(),
    )
}
