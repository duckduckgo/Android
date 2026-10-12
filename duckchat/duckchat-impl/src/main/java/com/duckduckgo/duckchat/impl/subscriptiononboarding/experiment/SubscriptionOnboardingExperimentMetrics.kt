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

package com.duckduckgo.duckchat.impl.subscriptiononboarding.experiment

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.send
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SubscriptionOnboardingExperimentMetrics {

    suspend fun fireAiFeaturesDisabled()
}

@ContributesBinding(AppScope::class)
class RealSubscriptionOnboardingExperimentMetrics @Inject constructor(
    private val inventory: FeatureTogglesInventory,
) : SubscriptionOnboardingExperimentMetrics {

    override suspend fun fireAiFeaturesDisabled() {
        experimentToggles().forEach { toggle ->
            MetricsPixel(
                metric = "ai_features_disabled",
                type = MetricType.NORMAL,
                value = "1",
                toggle = toggle,
                conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 1)),
            ).send()
        }
    }

    private suspend fun experimentToggles(): List<Toggle> =
        inventory.getAllTogglesForParent(PRIVACY_PRO_FEATURE_NAME).filter { it.featureName().name in EXPERIMENT_NAMES }

    private companion object {
        const val PRIVACY_PRO_FEATURE_NAME = "privacyPro"
        val EXPERIMENT_NAMES = setOf(
            "subscriptionOnboardingFreeTrialsOct2026",
            "subscriptionOnboardingPaidSubsOct2026",
        )
    }
}
