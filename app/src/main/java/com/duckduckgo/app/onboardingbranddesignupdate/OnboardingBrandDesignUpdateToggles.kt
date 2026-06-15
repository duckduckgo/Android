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

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "onboardingBrandDesignUpdate",
)
interface OnboardingBrandDesignUpdateToggles {

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun brandDesignUpdate(): Toggle

    /**
     * Gates the new fire animation work: new Inferno default in Data Clearing
     * settings + bottom-sheet Lottie swap.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun fireAnimationUpdate(): Toggle

    /**
     * Gates new-tab onboarding bubble improvements: showing/hiding the waving Dax and shark fin
     * based on whether they fit below the bubble (keyboard state, orientation, per-bubble sizing).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun onboardingImprovements(): Toggle
}
