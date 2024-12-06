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

package com.duckduckgo.app.onboarding.ui.page.extendedonboarding

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantConfig
import com.duckduckgo.experiments.api.VariantManager
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface HighlightsOnboardingExperimentManager {
    fun setExperimentVariants()
    fun isHighlightsEnabled(): Boolean
}

@ContributesBinding(AppScope::class)
class HighlightsOnboardingExperimentManagerImpl @Inject constructor(
    private val variantManager: VariantManager,
) : HighlightsOnboardingExperimentManager {

    private val isExtendedOnboardingEnabled: Boolean = true

    override fun setExperimentVariants() {
        val variants = listOf(
            VariantConfig("mw", 0.0, null), // Control variant
            VariantConfig("mx", 0.0, null), // Experimental variant
        )
        variantManager.updateVariants(variants)
    }

    override fun isHighlightsEnabled(): Boolean {
        return isExtendedOnboardingEnabled && variantManager.getVariantKey() == "mx"
    }
}
