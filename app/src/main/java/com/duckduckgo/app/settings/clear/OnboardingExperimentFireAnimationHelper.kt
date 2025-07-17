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

package com.duckduckgo.app.settings.clear

import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles
import javax.inject.Inject
import kotlin.io.path.Path

class OnboardingExperimentFireAnimationHelper @Inject constructor(
    private val onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles,
) {

    /**
     * Returns the resource ID for the selected fire animation.
     * If the selected animation is HeroFire returns the buck_experiment_fire animation.
     */
    fun getSelectedFireAnimationResId(selectedFireAnimation: FireAnimation): Int {
        return if (selectedFireAnimation is FireAnimation.HeroFire) {
            when {
                onboardingDesignExperimentToggles.buckOnboarding().isEnabled() -> R.raw.buck_experiment_fire
                onboardingDesignExperimentToggles.bbOnboarding().isEnabled() -> R.raw.bb_experiment_fire_optimised
                else -> R.raw.hero_fire_inferno
            }
        } else {
            selectedFireAnimation.resId
        }
    }
}
