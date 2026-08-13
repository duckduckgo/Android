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

package com.duckduckgo.common.ui.store

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

/**
 * App-wide, theme-related design changes feature flag, excluding onboarding which is gated via
 * `OnboardingBrandDesignUpdateToggles`.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "appBrandDesignUpdate",
)
interface AppBrandDesignUpdateToggles {

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun self(): Toggle

    /**
     * Off leaves Change app icon below the theme and night mode settings on the Appearance screen.
     * */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun appIcon(): Toggle

    /**
     * Gates theme-level design changes. This currently includes the rebrand button styling and
     * theme-specific accent blue; future theme-controlled changes should use this sub-toggle.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun theme(): Toggle

    /**
     * Gates the address bar radius, Lotties: shield, cookies, ad-blocking and Duck Player
     * assets, and the 40dp shield icon box.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun addressBar(): Toggle

    /** Only the pictogram swaps that change drawable type or call-site behaviour; the rest are flavour-gated. */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun pictograms(): Toggle
}
