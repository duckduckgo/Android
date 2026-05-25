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

import kotlinx.coroutines.flow.StateFlow

interface LinearOnboardingOrchestrator {
    val state: StateFlow<LinearOnboardingState>
    suspend fun startPlan(plan: LinearOnboardingPlan)
    suspend fun onEvent(event: LinearOnboardingEvent)
}

sealed interface LinearOnboardingState {
    data object NotStarted : LinearOnboardingState
    data class InProgress(
        val currentPlan: LinearOnboardingPlan,
        val currentStepIndex: Int,
    ) : LinearOnboardingState {
        val currentStep: LinearOnboardingStep
            get() = currentPlan.steps[currentStepIndex]
    }
    data object Completed : LinearOnboardingState
    data object Skipped : LinearOnboardingState
}

/**
 * A plan is a list of steps plus terminal callbacks. The orchestrator awaits
 * [onCompleted] / [onSkipped] before emitting the matching terminal state, so
 * any state written inside the callback is visible to listeners that route off
 * Completed / Skipped. Only the bottom-of-stack (main) plan's callbacks fire
 * when the orchestrator terminates; side plans only run on Return/AbortPlan.
 */
data class LinearOnboardingPlan(
    val steps: List<LinearOnboardingStep>,
    val onCompleted: suspend () -> Unit = {},
    val onSkipped: suspend () -> Unit = {},
)

typealias LinearOnboardingStepId = String

interface LinearOnboardingStep {
    val id: LinearOnboardingStepId
    val host: LinearOnboardingHost
    val precondition: suspend () -> Boolean
    val transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition
}

enum class LinearOnboardingHost {
    OnboardingActivity,
    BrowserActivity,
}

interface LinearOnboardingEvent

sealed interface LinearOnboardingTransition {
    /** Next eligible step in the current plan. */
    data object Advance : LinearOnboardingTransition

    /** Push a new frame; run [plan] from its first eligible step. */
    data class SwitchTo(val plan: LinearOnboardingPlan) : LinearOnboardingTransition

    /** Pop the top frame; advance the caller past the step that pushed. */
    data object Return : LinearOnboardingTransition

    /** Terminate the entire flow as Skipped (clears the whole frame stack). */
    data object AbortPlan : LinearOnboardingTransition

    /** Explicit no-op. */
    data object Stay : LinearOnboardingTransition
}
