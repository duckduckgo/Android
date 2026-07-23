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

import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundAnimator
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundStep

/**
 * Owns the background axis: which [OnboardingBackgroundStep] image is showing behind the dialog.
 * Thin diff wrapper over [OnboardingBackgroundAnimator] — this controller has no state of its own
 * beyond the animator it delegates to.
 */
class BackgroundController(private val animator: OnboardingBackgroundAnimator) {

    fun apply(previous: OnboardingBackgroundStep?, next: OnboardingBackgroundStep, animate: Boolean) {
        if (previous == next) return
        if (animate) animator.transitionTo(next) else animator.snapTo(next)
    }
}
