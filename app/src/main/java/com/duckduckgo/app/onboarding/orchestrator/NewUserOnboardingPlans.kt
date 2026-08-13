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

import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelAction
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelSender
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingStep
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.AbortPlan
import javax.inject.Inject

/**
 * Constructs the [LinearOnboardingPlan]s for the new-user flows. Every plan goes through here so each step
 * gets the cross-cutting wraps (shown pixels, dev skip) — plan builders never construct a
 * [LinearOnboardingPlan] directly.
 */
internal class NewUserOnboardingPlans @Inject constructor(
    private val onboardingPixelSender: OnboardingPixelSender,
    private val onboardingSteps: NewUserOnboardingSteps,
) {

    fun rootPlan(
        ctx: NewUserOnboardingPlanContext,
        steps: List<LinearOnboardingStep>,
        onCompleted: suspend () -> Unit,
        onSkipped: suspend () -> Unit,
    ): LinearOnboardingPlan =
        LinearOnboardingPlan(
            id = ROOT_PLAN_ID,
            steps = steps.firingShownPixels().abortingOnDevSkip(),
            onCompleted = onCompleted,
            onSkipped = onSkipped,
            result = { ctx.completionResult },
        )

    fun quickSetupPlan(ctx: NewUserOnboardingPlanContext, forceWithAiInput: Boolean = false): LinearOnboardingPlan =
        LinearOnboardingPlan(
            id = QUICK_SETUP_PLAN_ID,
            steps = listOf(onboardingSteps.quickSetupStep(ctx, forceWithAiInput)).firingShownPixels().abortingOnDevSkip(),
        )

    /**
     * Wraps each step so the internal dev "skip all onboarding" shortcut aborts the run from wherever
     * we are. The orchestrator still only routes [NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked] to
     * the current step's transition; this keeps that cross-cutting handling in one place instead of in
     * every step factory.
     */
    private fun List<LinearOnboardingStep>.abortingOnDevSkip(): List<LinearOnboardingStep> =
        map { step ->
            val original = step.transition
            val wrapped: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = { event ->
                if (event is NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked) AbortPlan else original(event)
            }
            when (step) {
                is NewUserOnboardingActivityStep -> step.copy(transition = wrapped)
                is NewUserBrowserActivityStep -> step.copy(transition = wrapped)
                else -> step
            }
        }

    /**
     * Wraps each step so that a [NewUserOnboardingEvent.Presented] event fires the step's shown pixel
     * (its [NewUserOnboardingActivityStep.pixelName], if any) and then delegates to the original transition.
     * This is the inner wrap, applied before [abortingOnDevSkip].
     */
    private fun List<LinearOnboardingStep>.firingShownPixels(): List<LinearOnboardingStep> =
        map { step ->
            val pixelName = (step as? NewUserOnboardingActivityStep)?.pixelName
                ?: (step as? NewUserBrowserActivityStep)?.pixelName
            val original = step.transition
            val wrapped: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = { event ->
                if (event is NewUserOnboardingEvent.Presented && pixelName != null) {
                    onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Shown)
                }
                original(event)
            }
            when (step) {
                is NewUserOnboardingActivityStep -> step.copy(transition = wrapped)
                is NewUserBrowserActivityStep -> step.copy(transition = wrapped)
                else -> step
            }
        }

    companion object {

        const val ROOT_PLAN_ID = "new-user_onboarding"
        const val QUICK_SETUP_PLAN_ID = "new-user_quick-setup"
    }
}
