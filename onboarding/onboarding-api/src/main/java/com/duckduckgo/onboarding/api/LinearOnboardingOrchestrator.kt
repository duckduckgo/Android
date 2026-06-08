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

package com.duckduckgo.onboarding.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Drives one linear onboarding run at a time.
 *
 * A run is a [LinearOnboardingPlan] (ordered steps plus terminal callbacks). The orchestrator walks it,
 * skipping steps whose [LinearOnboardingStep.precondition] is false, and exposes the current position as
 * [state]. A step can branch by returning a [LinearOnboardingTransition]: [LinearOnboardingTransition.SwitchTo]
 * pushes a side plan, [LinearOnboardingTransition.Return] pops back to its caller.
 *
 * It never touches UI: renderers observe [state] and report user actions via [onEvent].
 *
 * Implementations are thread-safe and serialise calls. Step and plan callbacks run inside that
 * serialization, so they must not call back into [startPlan] / [onEvent] — a reentrant call throws
 * IllegalStateException rather than deadlocking.
 */
interface LinearOnboardingOrchestrator {
    /**
     * Current position. [LinearOnboardingState.NotStarted] until the first [startPlan], then
     * [LinearOnboardingState.InProgress] through to a terminal [LinearOnboardingState.Completed] /
     * [LinearOnboardingState.Skipped]; restarting from a terminal state returns to InProgress. Never
     * NotStarted again once started.
     */
    val state: StateFlow<LinearOnboardingState>

    /**
     * Begins a run with [plan] from its first eligible step; [state] is up to date by the time this returns
     * (InProgress, or terminal if no step is eligible). Allowed from NotStarted or a terminal state; no-op
     * while a run is in progress.
     */
    suspend fun startPlan(plan: LinearOnboardingPlan)

    /**
     * Forwards [event] to the current step's [LinearOnboardingStep.transition] and applies the result.
     * No-op before start or after terminal.
     */
    suspend fun onEvent(event: LinearOnboardingEvent)
}

/** Where a run is: not yet started, mid-flow on a given step, or finished (completed / skipped). */
sealed interface LinearOnboardingState {
    /** No run has started. */
    data object NotStarted : LinearOnboardingState

    /**
     * A run that has started (in progress or finished). [rootPlanId] is the root (bottom-of-stack) plan's id,
     * the flow this run belongs to, so a consumer can scope to one flow regardless of the current step or
     * side plan.
     */
    sealed interface Started : LinearOnboardingState {
        val rootPlanId: LinearOnboardingPlanId
    }

    /**
     * Paused on [currentStep] (the [currentStepIndex]-th step of [currentPlan]) awaiting the next event.
     * [currentPlan] is the top-of-stack frame, which may be a side plan; [rootPlanId] is the root.
     */
    data class InProgress(
        override val rootPlanId: LinearOnboardingPlanId,
        val currentPlan: LinearOnboardingPlan,
        val currentStepIndex: Int,
    ) : Started {
        val currentStep: LinearOnboardingStep
            get() = currentPlan.steps[currentStepIndex]
    }

    /**
     * Reached by walking off the end of the root plan. [result] is the root plan's outcome (e.g. a query to
     * launch on handoff to the browser), or null. Terminal until a new run starts.
     */
    data class Completed(
        override val rootPlanId: LinearOnboardingPlanId,
        val result: LinearOnboardingResult? = null,
    ) : Started

    /** The run finished early via [LinearOnboardingTransition.AbortPlan]. Terminal until a new run is started. */
    data class Skipped(override val rootPlanId: LinearOnboardingPlanId) : Started
}

/**
 * Opaque marker for what a completed run produced (e.g. a pending query). The orchestrator only carries the
 * root plan's [LinearOnboardingPlan.result] into [LinearOnboardingState.Completed.result]; concrete types
 * live with the plan provider.
 */
interface LinearOnboardingResult

/**
 * Ordered [steps] plus terminal callbacks. [onCompleted] / [onSkipped] run before the matching terminal state
 * is emitted, and on completion [result] is carried into [LinearOnboardingState.Completed.result].
 * Only the root plan's callbacks and [result] fire.
 * A side plan (pushed via [LinearOnboardingTransition.SwitchTo]) that aborts or exhausts surfaces through the root.
 */
data class LinearOnboardingPlan(
    val id: LinearOnboardingPlanId,
    val steps: List<LinearOnboardingStep>,
    val onCompleted: suspend () -> Unit = {},
    val onSkipped: suspend () -> Unit = {},
    val result: suspend () -> LinearOnboardingResult? = { null },
)

/** Stable, human-readable identifier for a plan (e.g. for filtering/telemetry). Not persisted. */
typealias LinearOnboardingPlanId = String

/** Stable, human-readable identifier for a step (e.g. for logging/telemetry). Not persisted. */
typealias LinearOnboardingStepId = String

/**
 * One step of a plan. Concrete steps live with the plan provider; this is the minimum the
 * orchestrator needs to walk a plan. Renderer-specific data (which dialog to show, etc.) is added by
 * host-specific subtypes the renderer downcasts to.
 */
interface LinearOnboardingStep {
    /** Identifies the step within its plan. See [LinearOnboardingStepId]. */
    val id: LinearOnboardingStepId

    /** Which host renders this step. The orchestrator carries it so a renderer can route the handoff. */
    val host: LinearOnboardingHost

    /** Evaluated as the orchestrator advances; when false the step is skipped (not shown). */
    val precondition: suspend () -> Boolean

    /** Maps an incoming event to the next [LinearOnboardingTransition]. Returns [LinearOnboardingTransition.Stay] to ignore it. */
    val transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition
}

/** The screen that renders a step. The orchestrator only tags steps with it; routing is the caller's job. */
enum class LinearOnboardingHost {
    OnboardingActivity,
    BrowserActivity,
}

/**
 * Opaque marker; the orchestrator only routes events to [LinearOnboardingStep.transition]. Concrete event
 * types live with the plan provider.
 */
interface LinearOnboardingEvent

sealed interface LinearOnboardingTransition {
    /** Move to the next eligible step in the current plan; with none left, the run completes via the root plan. */
    data object Advance : LinearOnboardingTransition

    /**
     * Push a new frame and run [plan] from its first eligible step. The side plan resumes the caller only via
     * [Return]; running off its end (no [Return]) completes the whole run via the root plan.
     */
    data class SwitchTo(val plan: LinearOnboardingPlan) : LinearOnboardingTransition

    /** Pop the top frame; advance the caller past the step that pushed. */
    data object Return : LinearOnboardingTransition

    /** Terminate the entire flow as Skipped (clears the whole frame stack). */
    data object AbortPlan : LinearOnboardingTransition

    /** Explicit no-op. */
    data object Stay : LinearOnboardingTransition
}

/**
 * States of the run for [planId], in-progress and terminal; [LinearOnboardingState.NotStarted] and other
 * plans' states never match. A new collector receives the current matching state on subscription.
 */
fun Flow<LinearOnboardingState>.forPlan(planId: LinearOnboardingPlanId): Flow<LinearOnboardingState.Started> =
    filterIsInstance<LinearOnboardingState.Started>().filter { it.rootPlanId == planId }
