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

import com.duckduckgo.codegen.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "segmentedOnboarding",
)
interface SegmentedOnboardingFeatureToggles {

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun onboardingFlowByDownloadReasonExperiment(): Toggle

    /**
     * Shows the segmented onboarding flow to new users.
     *
     * Remote config usually arrives after the first run has picked its onboarding plan, so the code default decides for most users.
     * To roll back, release a patch that flips this default to FALSE.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun onboardingFlowByDownloadReason(): Toggle
}
