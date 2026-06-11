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

package com.duckduckgo.app.onboardingquicksetup

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName

/**
 * Feature toggles for the reinstaller quick-setup onboarding experiment.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "onboardingQuickSetup",
)
interface OnboardingQuickSetupToggles {

    /**
     * Main toggle for the onboarding quick-setup feature.
     * Default value: false (disabled).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    /**
     * A/B test gating the re-installer quick-setup screen. Only users in the [QuickSetupCohorts.TREATMENT]
     * cohort should be shown the quick-setup screen during onboarding.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun onboardingQuickSetupExperimentJun3(): Toggle

    enum class QuickSetupCohorts(override val cohortName: String) : CohortName {
        CONTROL("control"),
        TREATMENT("treatment"),
    }
}
