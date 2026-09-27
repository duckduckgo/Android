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

package com.duckduckgo.app.onboarding

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName

/**
 * Feature toggles for the experiment offering a password import step in the new-user onboarding.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "onboardingPasswordImport",
)
interface OnboardingPasswordImportToggles {

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle

    /** Assigns the cohort deciding whether the password-import step is part of the onboarding plan. */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun passwordImportExperimentAug25(): Toggle

    enum class OnboardingPasswordImportCohorts(override val cohortName: String) : CohortName {
        CONTROL("control"),
        TREATMENT("treatment"),
    }
}
