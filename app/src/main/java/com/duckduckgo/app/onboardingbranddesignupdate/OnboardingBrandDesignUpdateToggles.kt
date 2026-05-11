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

package com.duckduckgo.app.onboardingbranddesignupdate

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.Experiment
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName

/**
 * Feature toggles for the onboarding brand design updates.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "onboardingBrandDesignUpdate",
)
interface OnboardingBrandDesignUpdateToggles {

    /**
     * Main toggle for the onboarding brand design update feature.
     * Default value: false (disabled).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    /**
     * Toggle for the brand design update variant.
     * Default value: false (disabled).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun brandDesignUpdate(): Toggle

    /**
     * A/B test gating the re-installer quick-setup screen. Only users in the [QuickSetupCohorts.TREATMENT]
     * cohort should be shown the quick-setup screen during onboarding.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    @Experiment
    fun quickSetup(): Toggle

    enum class QuickSetupCohorts(override val cohortName: String) : CohortName {
        CONTROL("control"),
        TREATMENT("treatment"),
    }
}
