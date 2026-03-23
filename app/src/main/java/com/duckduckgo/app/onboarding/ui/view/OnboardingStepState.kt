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

package com.duckduckgo.app.onboarding.ui.view

/**
 * Pure state for the onboarding step indicator — no Android dependencies.
 *
 * Tracks the current step and total steps, with safe clamping.
 */
internal class OnboardingStepState {

    var totalSteps: Int = 0
        private set

    var currentStep: Int = 1
        private set

    /** 0-based index for the active dot (currentStep - 1). */
    val activeStepIndex: Int get() = currentStep - 1

    /** Whether there is a next step to advance to. */
    val hasNextStep: Boolean get() = currentStep < totalSteps

    /**
     * Set up the step count and initial step.
     *
     * @param totalSteps Total number of steps, clamped to >= 1.
     * @param currentStep Initial active step (1-based), clamped to [1, totalSteps].
     */
    fun setSteps(totalSteps: Int, currentStep: Int = 1) {
        this.totalSteps = totalSteps.coerceAtLeast(1)
        this.currentStep = currentStep.coerceIn(1, this.totalSteps)
    }

    /**
     * Jump to a specific step without animation.
     *
     * @param step 1-based step, clamped to [1, totalSteps].
     */
    fun setCurrentStep(step: Int) {
        currentStep = step.coerceIn(1, totalSteps.coerceAtLeast(1))
    }

    /**
     * Advance to the next step. Call this after an animation completes.
     * Does nothing if already at the last step.
     */
    fun advanceToNextStep() {
        if (hasNextStep) {
            currentStep++
        }
    }
}
