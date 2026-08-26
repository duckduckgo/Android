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

import com.duckduckgo.app.pixels.OnboardingPixelName
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingHost
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingStep
import com.duckduckgo.onboarding.api.LinearOnboardingStepId
import com.duckduckgo.onboarding.api.LinearOnboardingTransition

/**
 * An [com.duckduckgo.app.onboarding.ui.OnboardingActivity]-hosted step. Adds a [resolveDialog] rendering hook to the generic
 * [LinearOnboardingStep]; the VM reads it to know what to present for the current step.
 *
 * [indicator] places the step in the onboarding progress indicator. The "step N of M" the renderer shows is
 * derived from the position of [StepIndicatorMode.COUNTED] steps in the plan (see [stepIndicatorProgress]), so
 * numbering follows plan order with no hardcoded page numbers.
 */
data class NewUserOnboardingActivityStep(
    override val id: LinearOnboardingStepId,
    val pixelName: OnboardingPixelName?,
    override val host: LinearOnboardingHost = LinearOnboardingHost.OnboardingActivity,
    override val precondition: suspend () -> Boolean = { true },
    override val transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition,
    val indicator: StepIndicatorMode = StepIndicatorMode.NONE,
    val resolveDialog: suspend () -> NewUserOnboardingActivityDialog,
) : LinearOnboardingStep

/** How a step appears in the progress indicator. */
enum class StepIndicatorMode {
    /** No indicator at all. */
    NONE,

    /** Takes the next position in the sequence, and counts towards the total. */
    COUNTED,

    /**
     * Shows the number of the [COUNTED] step before it, and adds nothing to the total. For a step that continues
     * an interaction the user already started — the outcome of the step they are on, not a step of its own.
     */
    CONTINUES_PREVIOUS,
}

/** 1-based [current] position of a step within its plan's step-indicator sequence, and the sequence length [total]. */
data class StepProgress(val current: Int, val total: Int)

/**
 * Progress to show in the onboarding step indicator for the current step, or null when the current step shows
 * none. Derived from the position of [StepIndicatorMode.COUNTED] steps in the current plan, so adding or
 * reordering steps keeps the numbering correct without touching hardcoded page numbers.
 *
 * Counting up to and including the current index gives a [StepIndicatorMode.COUNTED] step its own position and
 * a [StepIndicatorMode.CONTINUES_PREVIOUS] step the position of the counted step before it. A plan that opens
 * with a continuing step has nothing to continue, and reports position 0.
 */
fun LinearOnboardingState.InProgress.stepIndicatorProgress(): StepProgress? {
    val step = currentStep as? NewUserOnboardingActivityStep ?: return null
    if (step.indicator == StepIndicatorMode.NONE) return null
    val countedIndices = currentPlan.steps.indices.filter {
        (currentPlan.steps[it] as? NewUserOnboardingActivityStep)?.indicator == StepIndicatorMode.COUNTED
    }
    return StepProgress(current = countedIndices.count { it <= currentStepIndex }, total = countedIndices.size)
}
