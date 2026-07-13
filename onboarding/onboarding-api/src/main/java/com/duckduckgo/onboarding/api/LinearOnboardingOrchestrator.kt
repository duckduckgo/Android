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
 * Drives one linear onboarding run at a time. It walks a [LinearOnboardingPlan]'s steps, skips any whose
 * [LinearOnboardingStep.precondition] is false, and publishes the current position as [state]. A step can branch
 * by returning a [LinearOnboardingTransition]. [LinearOnboardingTransition.SwitchTo] pushes a side plan, and
 * [LinearOnboardingTransition.ReturnAndAdvance] pops back to its caller.
 *
 * It never touches UI. Renderers observe [state] and report user actions through [onEvent].
 *
 * Implementations are thread-safe and run calls one at a time. Step and plan callbacks run inside that same
 * serialization, so they must not call back into [startPlan] or [onEvent]. A reentrant call throws
 * IllegalStateException instead of deadlocking.
 */
interface LinearOnboardingOrchestrator {
    /**
     * Where the run currently is. It starts as [LinearOnboardingState.NotStarted], becomes
     * [LinearOnboardingState.InProgress] on the first [startPlan], and ends at [LinearOnboardingState.Completed]
     * or [LinearOnboardingState.Skipped]. Starting again from a terminal state goes back to InProgress. Once a
     * run has started it is never NotStarted again.
     */
    val state: StateFlow<LinearOnboardingState>

    /**
     * Starts a run with [plan] from its first eligible step. [state] is up to date by the time this returns.
     * You can call it from NotStarted or a terminal state. It does nothing while a run is already in progress.
     */
    suspend fun startPlan(plan: LinearOnboardingPlan)

    /**
     * Passes [event] to the current step's [LinearOnboardingStep.transition] and applies the result.
     * Does nothing before the run starts or after it ends.
     */
    suspend fun onEvent(event: LinearOnboardingEvent)
}

/** Where a run is. Either not yet started, paused on a step, or finished (completed or skipped). */
sealed interface LinearOnboardingState {
    data object NotStarted : LinearOnboardingState

    /**
     * A run that has started, whether in progress or finished. [rootPlanId] is the id of the root plan, the one
     * at the bottom of the stack. It stays the same across side plans, so a consumer can scope to a single flow
     * no matter which step is current.
     */
    sealed interface Started : LinearOnboardingState {
        val rootPlanId: LinearOnboardingPlanId
    }

    /**
     * Paused on [currentStep], the [currentStepIndex]-th step of [currentPlan], waiting for the next event.
     * [currentPlan] is the frame on top of the stack and may be a side plan. [rootPlanId] is the root plan.
     * [canGoBack] is true when an earlier step was shown in this run.
     */
    data class InProgress(
        override val rootPlanId: LinearOnboardingPlanId,
        val currentPlan: LinearOnboardingPlan,
        val currentStepIndex: Int,
        val canGoBack: Boolean = false,
    ) : Started {
        val currentStep: LinearOnboardingStep
            get() = currentPlan.steps[currentStepIndex]
    }

    /**
     * Reached by running past the last step of the top plan. [result] is the root plan's outcome, such as a
     * query to launch when handing off to the browser, or null. This state is terminal.
     */
    data class Completed(
        override val rootPlanId: LinearOnboardingPlanId,
        val result: LinearOnboardingResult? = null,
    ) : Started

    /** Reached through [LinearOnboardingTransition.AbortPlan], which clears the whole frame stack. This state is terminal. */
    data class Skipped(override val rootPlanId: LinearOnboardingPlanId) : Started
}

/** A marker for whatever a completed run produced, such as a pending query. Concrete types live with the plan provider. */
interface LinearOnboardingResult

/**
 * An ordered list of [steps] with callbacks for the end of the run. [onCompleted] and [onSkipped] run just
 * before the matching terminal state, and on completion [result] is passed into
 * [LinearOnboardingState.Completed.result]. Only the root plan's callbacks and [result] run. When a side plan
 * aborts or runs out of steps, that outcome surfaces through the root.
 */
data class LinearOnboardingPlan(
    val id: LinearOnboardingPlanId,
    val steps: List<LinearOnboardingStep>,
    val onCompleted: suspend () -> Unit = {},
    val onSkipped: suspend () -> Unit = {},
    val result: suspend () -> LinearOnboardingResult? = { null },
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LinearOnboardingPlan

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

/** A stable, human-readable id for a plan, handy for filtering and telemetry. Not persisted. */
typealias LinearOnboardingPlanId = String

/** A stable, human-readable id for a step, handy for logging and telemetry. Not persisted. */
typealias LinearOnboardingStepId = String

/**
 * The least the orchestrator needs to walk a plan. Concrete steps live with the plan provider. Anything a
 * renderer needs, like which dialog to show, goes on host-specific subtypes that the renderer downcasts to.
 */
interface LinearOnboardingStep {
    val id: LinearOnboardingStepId

    val host: LinearOnboardingHost

    /** Checked as the orchestrator advances. When it returns false the step is skipped and not shown. */
    val precondition: suspend () -> Boolean

    /** Turns an incoming event into the next [LinearOnboardingTransition]. Return [LinearOnboardingTransition.Stay] to ignore the event. */
    val transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition
}

/** The screen that renders a step. The orchestrator only tags steps with it. Routing is up to the caller. */
enum class LinearOnboardingHost {
    OnboardingActivity,
    BrowserActivity,
    SubscriptionOnboardingActivity,
}

/** A marker for events. Concrete event types live with the plan provider. The orchestrator only routes them to [LinearOnboardingStep.transition]. */
interface LinearOnboardingEvent

sealed interface LinearOnboardingTransition {
    /** Move to the next eligible step in the current plan. If none are left, the run completes through the root plan. */
    data object Advance : LinearOnboardingTransition

    /**
     * Push a new frame and run [plan] from its first eligible step. The caller only resumes if the side plan
     * ends with [ReturnAndAdvance]. If the side plan runs past its last step without a [ReturnAndAdvance], the whole run completes
     * through the root plan.
     */
    data class SwitchTo(val plan: LinearOnboardingPlan) : LinearOnboardingTransition

    /** Pop the top frame and advance the caller past the step that pushed it. */
    data object ReturnAndAdvance : LinearOnboardingTransition

    /** Return to the previously shown step. No-op on the first shown step, where nothing earlier exists. */
    data object GoBack : LinearOnboardingTransition

    /** End the entire flow as Skipped and clear the whole frame stack. */
    data object AbortPlan : LinearOnboardingTransition

    data object Stay : LinearOnboardingTransition
}

/**
 * The states of the run for [planId], both in-progress and terminal. [LinearOnboardingState.NotStarted] and
 * states from other plans never match. A new collector gets the current matching state as soon as it subscribes.
 */
fun Flow<LinearOnboardingState>.forPlan(planId: LinearOnboardingPlanId): Flow<LinearOnboardingState.Started> =
    filterIsInstance<LinearOnboardingState.Started>().filter { it.rootPlanId == planId }
