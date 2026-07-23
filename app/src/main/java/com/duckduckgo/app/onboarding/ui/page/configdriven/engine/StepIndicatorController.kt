/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.configdriven.engine

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View
import androidx.core.view.isVisible
import com.duckduckgo.app.onboarding.orchestrator.StepProgress
import com.duckduckgo.app.onboarding.ui.view.OnboardingStepIndicatorView

/**
 * Owns the step-indicator axis: the "X of Y" pill's visibility and its snap-vs-advance-one-step
 * choreography. Ported from `OnboardingStepIndicatorView.showStep`/`animateToStep` (:2984-2995)
 * and the input-screen-preview fade-out (:1464-1478, the only dialog with no step indicator at all).
 */
class StepIndicatorController(private val indicator: OnboardingStepIndicatorView) {

    private var fadeOut: ObjectAnimator? = null

    /**
     * @param previous The step shown before this call, or null if none was showing.
     * @param next The step to show now, or null to hide the indicator entirely.
     * @param animate Whether to animate the transition (fade-out when hiding; advance-one-step
     *   when [previous] was already showing) or snap directly to the end state.
     */
    fun apply(previous: StepProgress?, next: StepProgress?, animate: Boolean) {
        fadeOut?.cancel()
        fadeOut = null

        when {
            next == null && previous != null && animate -> {
                fadeOut = ObjectAnimator.ofFloat(indicator, View.ALPHA, 1f, 0f).apply {
                    duration = OUTRO_FADE_DURATION
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            indicator.isVisible = false
                        }
                    })
                    start()
                }
            }
            next == null -> {
                indicator.alpha = 1f
                indicator.isVisible = false
            }
            !animate || previous == null -> {
                indicator.alpha = 1f
                indicator.isVisible = true
                indicator.setSteps(totalSteps = next.total, currentStep = next.current)
            }
            else -> {
                indicator.alpha = 1f
                indicator.isVisible = true
                indicator.setSteps(totalSteps = next.total, currentStep = next.current - 1)
                indicator.animateToNextStep()
            }
        }
    }

    fun skipRunning() {
        fadeOut?.end()
    }

    fun release() {
        fadeOut?.cancel()
        fadeOut = null
    }

    private companion object {
        const val OUTRO_FADE_DURATION = 300L
    }
}
