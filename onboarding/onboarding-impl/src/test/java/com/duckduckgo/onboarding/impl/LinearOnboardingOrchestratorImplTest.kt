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

import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingHost
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingResult
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingState.Completed
import com.duckduckgo.onboarding.api.LinearOnboardingState.InProgress
import com.duckduckgo.onboarding.api.LinearOnboardingState.NotStarted
import com.duckduckgo.onboarding.api.LinearOnboardingState.Skipped
import com.duckduckgo.onboarding.api.LinearOnboardingStep
import com.duckduckgo.onboarding.api.LinearOnboardingStepId
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.AbortPlan
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Advance
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.GoBack
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.ReturnAndAdvance
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Stay
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.SwitchTo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinearOnboardingOrchestratorImplTest {

    private val testee = LinearOnboardingOrchestratorImpl()

    companion object {
        private const val PLAN_ID = "test_plan"
        private const val SIDE_PLAN_ID = "side_plan"
    }

    private object Next : LinearOnboardingEvent
    private object Back : LinearOnboardingEvent

    // A step that goes back on [Back] and advances on anything else, for exercising GoBack.
    private fun backAwareStep(
        id: String,
        precondition: suspend () -> Boolean = { true },
    ): LinearOnboardingStep = step(
        id = id,
        precondition = precondition,
        transition = { event -> if (event is Back) GoBack else Advance },
    )

    private fun step(
        id: String,
        precondition: suspend () -> Boolean = { true },
        host: LinearOnboardingHost = LinearOnboardingHost.OnboardingActivity,
        transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = { Advance },
    ): LinearOnboardingStep = object : LinearOnboardingStep {
        override val id: LinearOnboardingStepId = id
        override val host: LinearOnboardingHost = host
        override val precondition: suspend () -> Boolean = precondition
        override val transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = transition
    }

    @Test
    fun `when start plan then advances to first eligible step`() = runTest {
        testee.startPlan(LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a"), step("b"))))

        val state = testee.state.value
        assertTrue(state is InProgress)
        assertEquals("a", (state as InProgress).currentStep.id)
    }

    @Test
    fun `when start plan then skips steps with false precondition`() = runTest {
        testee.startPlan(
            LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a", precondition = { false }), step("b"))),
        )

        assertEquals("b", (testee.state.value as InProgress).currentStep.id)
    }

    @Test
    fun `when all preconditions false then completes immediately`() = runTest {
        testee.startPlan(
            LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a", precondition = { false }))),
        )

        assertEquals(Completed(rootPlanId = PLAN_ID), testee.state.value)
    }

    @Test
    fun `when start plan called while in progress then second call is no op and state unchanged`() = runTest {
        testee.startPlan(LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a"))))

        testee.startPlan(LinearOnboardingPlan(id = "other_plan", steps = listOf(step("z"))))

        assertEquals("a", (testee.state.value as InProgress).currentStep.id)
    }

    @Test
    fun `when start plan returns then state is already up to date`() = runTest {
        testee.startPlan(LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a"))))

        // No onEvent/advance between startPlan returning and this read: startPlan must have
        // published the started state before it returned, as its contract promises.
        val state = testee.state.value
        assertTrue(state is InProgress)
        assertEquals("a", (state as InProgress).currentStep.id)
    }

    @Test
    fun `when start plan with no eligible steps then state is already completed on return`() = runTest {
        testee.startPlan(LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a", precondition = { false }))))

        // An immediate-terminal outcome is also visible by the time startPlan returns.
        assertEquals(Completed(rootPlanId = PLAN_ID), testee.state.value)
    }

    @Test
    fun `when plan has a result then completed carries it`() = runTest {
        val planResult = object : LinearOnboardingResult {}
        testee.startPlan(LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a")), result = { planResult }))

        testee.onEvent(Next) // advance past "a" -> Completed

        assertEquals(Completed(rootPlanId = PLAN_ID, result = planResult), testee.state.value)
    }

    @Test
    fun `when plan has no result then completed result is null`() = runTest {
        testee.startPlan(LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a"))))

        testee.onEvent(Next)

        val state = testee.state.value
        assertEquals(Completed(rootPlanId = PLAN_ID, result = null), state)
        assertEquals(PLAN_ID, (state as Completed).rootPlanId)
    }

    @Test
    fun `when start plan from completed then starts a new run`() = runTest {
        testee.startPlan(LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a"))))
        testee.onEvent(Next) // advance past "a" -> Completed
        assertEquals(Completed(rootPlanId = PLAN_ID), testee.state.value)

        testee.startPlan(LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("b"))))

        assertEquals("b", (testee.state.value as InProgress).currentStep.id)
    }

    @Test
    fun `when start plan from skipped then starts a new run`() = runTest {
        testee.startPlan(LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a", transition = { AbortPlan }))))
        testee.onEvent(Next) // -> Skipped
        assertEquals(Skipped(rootPlanId = PLAN_ID), testee.state.value)

        testee.startPlan(LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("b"))))

        assertEquals("b", (testee.state.value as InProgress).currentStep.id)
    }

    @Test
    fun `when restart same plan after completion then runs again from first step`() = runTest {
        val plan = LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a"), step("b")))
        testee.startPlan(plan)
        testee.onEvent(Next) // a -> b
        testee.onEvent(Next) // b -> Completed
        assertEquals(Completed(rootPlanId = PLAN_ID), testee.state.value)

        testee.startPlan(plan)

        assertEquals("a", (testee.state.value as InProgress).currentStep.id)
    }

    @Test
    fun `when advance then moves to next eligible step`() = runTest {
        testee.startPlan(
            LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a"), step("b", precondition = { false }), step("c"))),
        )

        testee.onEvent(Next) // step "a" returns Advance by default

        assertEquals("c", (testee.state.value as InProgress).currentStep.id)
    }

    @Test
    fun `when advance past last step then completes`() = runTest {
        testee.startPlan(LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a"))))

        testee.onEvent(Next)

        val state = testee.state.value
        assertEquals(Completed(rootPlanId = PLAN_ID), state)
        assertEquals(PLAN_ID, (state as Completed).rootPlanId)
    }

    @Test
    fun `when step returns abort plan then skipped`() = runTest {
        testee.startPlan(
            LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a", transition = { AbortPlan }))),
        )

        testee.onEvent(Next)

        val state = testee.state.value
        assertEquals(Skipped(rootPlanId = PLAN_ID), state)
        assertEquals(PLAN_ID, (state as Skipped).rootPlanId)
    }

    @Test
    fun `when step returns stay then state unchanged`() = runTest {
        testee.startPlan(
            LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a", transition = { Stay }), step("b"))),
        )

        testee.onEvent(Next)

        assertEquals("a", (testee.state.value as InProgress).currentStep.id)
    }

    @Test
    fun `when on event before start then no op`() = runTest {
        testee.onEvent(Next)

        assertEquals(NotStarted, testee.state.value)
    }

    @Test
    fun `when on completed then runs before completed emitted`() = runTest {
        var stateInsideCallback: LinearOnboardingState? = null
        testee.startPlan(
            LinearOnboardingPlan(
                id = PLAN_ID,
                steps = listOf(step("a")),
                onCompleted = { stateInsideCallback = testee.state.value },
            ),
        )

        testee.onEvent(Next)

        // The callback observed a non-terminal state, proving it ran before Completed was emitted.
        assertTrue(stateInsideCallback is InProgress)
        assertEquals(Completed(rootPlanId = PLAN_ID), testee.state.value)
    }

    @Test
    fun `when on completed throws then run still terminates as completed and side effect runs once`() = runTest {
        var completedCount = 0
        testee.startPlan(
            LinearOnboardingPlan(
                id = PLAN_ID,
                steps = listOf(step("a")),
                onCompleted = {
                    completedCount++
                    error("completion failed")
                },
            ),
        )

        runCatching { testee.onEvent(Next) } // advance past "a" -> terminate; onCompleted throws

        // The run commits a terminal state (not wedged on InProgress) and is not retried.
        assertEquals(Completed(rootPlanId = PLAN_ID), testee.state.value)
        runCatching { testee.onEvent(Next) } // no-op; must not re-run onCompleted
        assertEquals(1, completedCount)
    }

    @Test
    fun `when immediate completion onCompleted throws then no orphan frame corrupts a later start`() = runTest {
        runCatching {
            testee.startPlan(
                LinearOnboardingPlan(
                    id = "first",
                    steps = listOf(step("a", precondition = { false })), // completes immediately
                    onCompleted = { error("completion failed") },
                ),
            )
        }

        // A later start must not be blocked by, nor inherit the rootPlanId of, an orphan frame.
        testee.startPlan(LinearOnboardingPlan(id = "second", steps = listOf(step("b"))))

        val state = testee.state.value as InProgress
        assertEquals("b", state.currentStep.id)
        assertEquals("second", state.rootPlanId)
    }

    @Test
    fun `when start fails on immediate completion then onEvent does not dispatch into a stale frame`() = runTest {
        runCatching {
            testee.startPlan(
                LinearOnboardingPlan(id = PLAN_ID, steps = emptyList(), onCompleted = { error("completion failed") }),
            )
        }

        testee.onEvent(Next) // must be a no-op, not an index crash into steps[0] of an orphan frame

        assertTrue(testee.state.value is Completed)
    }

    @Test
    fun `when start fails on a throwing precondition then onEvent is a no-op`() = runTest {
        runCatching {
            testee.startPlan(
                LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a", precondition = { error("precondition failed") }))),
            )
        }

        testee.onEvent(Next) // no run in progress -> must not dispatch into the abandoned frame

        assertEquals(NotStarted, testee.state.value)
    }

    @Test
    fun `when a callback reenters the orchestrator then it throws instead of deadlocking`() = runTest {
        testee.startPlan(
            LinearOnboardingPlan(
                id = PLAN_ID,
                steps = listOf(step("a")),
                onCompleted = { testee.onEvent(Next) }, // illegal reentry from a terminal callback
            ),
        )

        val error = runCatching { testee.onEvent(Next) }.exceptionOrNull() // advance past "a" -> terminate -> reentry

        assertTrue("expected IllegalStateException but was $error", error is IllegalStateException)
    }

    @Test
    fun `when on skipped then runs before skipped emitted`() = runTest {
        var stateInsideCallback: LinearOnboardingState? = null
        testee.startPlan(
            LinearOnboardingPlan(
                id = PLAN_ID,
                steps = listOf(step("a", transition = { AbortPlan })),
                onSkipped = { stateInsideCallback = testee.state.value },
            ),
        )

        testee.onEvent(Next)

        assertTrue(stateInsideCallback is InProgress)
        assertEquals(Skipped(rootPlanId = PLAN_ID), testee.state.value)
    }

    @Test
    fun `when switch to then runs side plan from first step`() = runTest {
        val sidePlan = LinearOnboardingPlan(id = SIDE_PLAN_ID, steps = listOf(step("side")))
        testee.startPlan(
            LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a", transition = { SwitchTo(sidePlan) }), step("b"))),
        )

        testee.onEvent(Next)

        val state = testee.state.value as InProgress
        assertEquals("side", state.currentStep.id)
        // rootPlanId must remain the ROOT plan's id, not the side plan's id.
        assertEquals(PLAN_ID, state.rootPlanId)
    }

    @Test
    fun `when return from side plan then resumes caller past switching step`() = runTest {
        val sidePlan = LinearOnboardingPlan(id = SIDE_PLAN_ID, steps = listOf(step("side", transition = { ReturnAndAdvance })))
        testee.startPlan(
            LinearOnboardingPlan(
                id = PLAN_ID,
                steps = listOf(
                    step("a", transition = { SwitchTo(sidePlan) }),
                    step("b"),
                ),
            ),
        )

        testee.onEvent(Next) // a -> SwitchTo(side); now on "side"
        testee.onEvent(Next) // side -> Return; pop, advance caller past "a" -> "b"

        val state = testee.state.value as InProgress
        assertEquals("b", state.currentStep.id)
        assertEquals(PLAN_ID, state.rootPlanId)
    }

    @Test
    fun `when side plan aborts then whole flow skipped via main on skipped`() = runTest {
        var mainSkippedRan = false
        val sidePlan = LinearOnboardingPlan(id = SIDE_PLAN_ID, steps = listOf(step("side", transition = { AbortPlan })))
        testee.startPlan(
            LinearOnboardingPlan(
                id = PLAN_ID,
                steps = listOf(step("a", transition = { SwitchTo(sidePlan) })),
                onSkipped = { mainSkippedRan = true },
            ),
        )

        testee.onEvent(Next) // a -> SwitchTo(side)
        testee.onEvent(Next) // side -> AbortPlan

        assertTrue(mainSkippedRan)
        val state = testee.state.value
        assertEquals(Skipped(rootPlanId = PLAN_ID), state)
        assertEquals(PLAN_ID, (state as Skipped).rootPlanId)
    }

    @Test
    fun `when side plan exhausts then completes via main without resuming the caller`() = runTest {
        var mainCompletedRan = false
        val sidePlan = LinearOnboardingPlan(id = SIDE_PLAN_ID, steps = listOf(step("side"))) // default transition Advance
        testee.startPlan(
            LinearOnboardingPlan(
                id = PLAN_ID,
                // "b" follows the switching step: running off the end of the side plan must complete the run,
                // not resume the main plan into "b". Only an explicit Return would resume the caller.
                steps = listOf(step("a", transition = { SwitchTo(sidePlan) }), step("b")),
                onCompleted = { mainCompletedRan = true },
            ),
        )

        testee.onEvent(Next) // a -> SwitchTo(side)
        testee.onEvent(Next) // side runs off its end -> whole run completes via main, does not resume "b"

        assertTrue(mainCompletedRan)
        val state = testee.state.value
        assertEquals(Completed(rootPlanId = PLAN_ID), state)
        assertEquals(PLAN_ID, (state as Completed).rootPlanId)
    }

    @Test
    fun `when on first step then can go back is false`() = runTest {
        testee.startPlan(LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a"), step("b"))))

        assertEquals(false, (testee.state.value as InProgress).canGoBack)
    }

    @Test
    fun `when advanced past first step then can go back is true`() = runTest {
        testee.startPlan(LinearOnboardingPlan(id = PLAN_ID, steps = listOf(step("a"), step("b"))))

        testee.onEvent(Next) // a -> b

        val state = testee.state.value as InProgress
        assertEquals("b", state.currentStep.id)
        assertEquals(true, state.canGoBack)
    }

    @Test
    fun `when go back then returns to previous step`() = runTest {
        testee.startPlan(
            LinearOnboardingPlan(id = PLAN_ID, steps = listOf(backAwareStep("a"), backAwareStep("b"))),
        )

        testee.onEvent(Next) // a -> b
        testee.onEvent(Back) // b -> back to a

        val state = testee.state.value as InProgress
        assertEquals("a", state.currentStep.id)
        // Back on the first shown step again would be a no-op.
        assertEquals(false, state.canGoBack)
    }

    @Test
    fun `when go back on first step then no op and state unchanged`() = runTest {
        testee.startPlan(
            LinearOnboardingPlan(id = PLAN_ID, steps = listOf(backAwareStep("a"), backAwareStep("b"))),
        )

        testee.onEvent(Back) // nothing earlier -> no-op

        val state = testee.state.value as InProgress
        assertEquals("a", state.currentStep.id)
        assertEquals(false, state.canGoBack)
    }

    @Test
    fun `when go back to a step whose precondition became false then it is still restored`() = runTest {
        // "a" is eligible when first shown, then becomes ineligible (e.g. marked complete). History-based back
        // must still restore it; a precondition-recomputing back could not.
        var aEligible = true
        testee.startPlan(
            LinearOnboardingPlan(
                id = PLAN_ID,
                steps = listOf(backAwareStep("a", precondition = { aEligible }), backAwareStep("b")),
            ),
        )

        testee.onEvent(Next) // a -> b
        aEligible = false // "a" would now be skipped by a forward advance
        testee.onEvent(Back) // b -> back to a, restored from history

        assertEquals("a", (testee.state.value as InProgress).currentStep.id)
    }

    @Test
    fun `when advance after go back then moves forward again`() = runTest {
        testee.startPlan(
            LinearOnboardingPlan(id = PLAN_ID, steps = listOf(backAwareStep("a"), backAwareStep("b"), backAwareStep("c"))),
        )

        testee.onEvent(Next) // a -> b
        testee.onEvent(Next) // b -> c
        testee.onEvent(Back) // c -> b
        assertEquals("b", (testee.state.value as InProgress).currentStep.id)

        testee.onEvent(Next) // b -> c again

        val state = testee.state.value as InProgress
        assertEquals("c", state.currentStep.id)
        assertEquals(true, state.canGoBack)
    }

    @Test
    fun `when go back across a switch to boundary then restores the caller step`() = runTest {
        val sidePlan = LinearOnboardingPlan(id = SIDE_PLAN_ID, steps = listOf(backAwareStep("side")))
        testee.startPlan(
            LinearOnboardingPlan(
                id = PLAN_ID,
                steps = listOf(
                    step("a", transition = { event -> if (event is Back) GoBack else SwitchTo(sidePlan) }),
                    backAwareStep("b"),
                ),
            ),
        )

        testee.onEvent(Next) // a -> SwitchTo(side); now on "side"
        assertEquals("side", (testee.state.value as InProgress).currentStep.id)

        testee.onEvent(Back) // side -> back to caller step "a"

        val state = testee.state.value as InProgress
        assertEquals("a", state.currentStep.id)
        assertEquals(PLAN_ID, state.rootPlanId)
    }
}
