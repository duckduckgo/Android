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

package com.duckduckgo.app.onboarding.ui.page.configdriven.engine

import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundAnimator
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundStep

/** Owns the background axis: which [OnboardingBackgroundStep] image is showing behind the dialog. */
interface BackgroundController {
    fun apply(previous: OnboardingBackgroundStep?, next: OnboardingBackgroundStep, animate: Boolean)
    fun skipRunning()

    fun release()
}

class BackgroundControllerImpl(private val animator: OnboardingBackgroundAnimator) : BackgroundController {

    /** Set while a transitionTo may still be animating, so [skipRunning] knows there is something to settle. */
    private var transitioningTo: OnboardingBackgroundStep? = null

    override fun apply(
        previous: OnboardingBackgroundStep?,
        next: OnboardingBackgroundStep,
        animate: Boolean,
    ) {
        if (previous == next) return
        if (animate) {
            transitioningTo = next
            animator.transitionTo(next, onAnimationEnd = { if (transitioningTo == next) transitioningTo = null })
        } else {
            transitioningTo = null
            animator.snapTo(next)
        }
    }

    override fun skipRunning() {
        transitioningTo?.let { animator.snapTo(it) }
        transitioningTo = null
    }

    override fun release() {
        animator.cancel()
    }
}
