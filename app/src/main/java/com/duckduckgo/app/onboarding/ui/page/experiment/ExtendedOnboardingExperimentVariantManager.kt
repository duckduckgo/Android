/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.experiment

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantConfig
import com.duckduckgo.experiments.api.VariantFilters
import com.duckduckgo.experiments.api.VariantManager
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface ExtendedOnboardingExperimentVariantManager {
    fun setExperimentVariants()
    fun isComparisonChartEnabled(): Boolean
    fun isAestheticUpdatesEnabled(): Boolean
}

@ContributesBinding(AppScope::class)
class ExtendedOnboardingExperimentVariantManagerImpl @Inject constructor(
    private val variantManager: VariantManager,
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles,
) : ExtendedOnboardingExperimentVariantManager {

    private val isExtendedOnboardingEnabled: Boolean = false

    override fun setExperimentVariants() {
        val variants = listOf(
            VariantConfig("ms", 0.0, VariantFilters(locale = listOf("en_US", "en_GB", "en_CA", "en_IN", "en_AU"))),
            VariantConfig("mt", 0.0, VariantFilters(locale = listOf("en_US", "en_GB", "en_CA", "en_IN", "en_AU"))),
        )
        variantManager.updateVariants(variants)
    }

    override fun isComparisonChartEnabled(): Boolean {
        val isRemoteFeatureEnabled = extendedOnboardingFeatureToggles.comparisonChart().isEnabled()
        val isLocalFeatureEnabled = isExtendedOnboardingEnabled && variantManager.getVariantKey() == "mt"
        return isRemoteFeatureEnabled || isLocalFeatureEnabled
    }

    override fun isAestheticUpdatesEnabled(): Boolean {
        return extendedOnboardingFeatureToggles.aestheticUpdates().isEnabled()
    }
}
