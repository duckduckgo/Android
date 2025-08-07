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

package com.duckduckgo.app.onboardingdesignexperiment

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles.Companion.BASE_EXPERIMENT_NAME
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = BASE_EXPERIMENT_NAME,
)
/**
 * Interface defining feature toggles for the onboarding design experiment.
 * These toggles control specific features related to the onboarding process.
 */
interface OnboardingDesignExperimentToggles {

    /**
     * Toggle for enabling or disabling the "self" onboarding design experiment.
     * Default value: false (disabled).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun onboardingDesignExperimentAug25(): Toggle

    enum class OnboardingDesignExperimentCohort(override val cohortName: String) : Toggle.State.CohortName {
        MODIFIED_CONTROL("modifiedControl"),
        BUCK("buck"),
        BB("bb"),
    }

    companion object {
        internal const val BASE_EXPERIMENT_NAME = "onboardingDesignExperiment"
    }
}


    /**
     * Toggle for enabling or disabling the "buckOnboarding" design experiment.
     * Default value: false (disabled).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun buckOnboarding(): Toggle

    /**
     * Toggle for enabling or disabling the "bbOnboarding" design experiment.
     * Default value: false (disabled).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun bbOnboarding(): Toggle
}
