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

package com.duckduckgo.pir.impl.onboarding.experiment

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

    suspend fun firePirActivated()
}

@ContributesBinding(AppScope::class)
class RealSubscriptionOnboardingExperimentMetrics @Inject constructor(
    private val inventory: FeatureTogglesInventory,
) : SubscriptionOnboardingExperimentMetrics {

    override suspend fun firePirActivated() {
        experimentToggles().forEach { toggle ->
            fire(toggle, metric = "pirActivated_d1", window = ConversionWindow(lowerWindow = 0, upperWindow = 1))
            when (toggle.featureName().name) {
                FREE_TRIALS_EXPERIMENT ->
                    fire(toggle, metric = "pirActivated_d2_7", window = ConversionWindow(lowerWindow = 2, upperWindow = 7))
                PAID_SUBS_EXPERIMENT ->
                    fire(toggle, metric = "pirActivated_d2_30", window = ConversionWindow(lowerWindow = 2, upperWindow = 30))
            }
        }
    }

    private suspend fun fire(
        toggle: Toggle,
        metric: String,
        window: ConversionWindow,
    ) {
        MetricsPixel(
            metric = metric,
            type = MetricType.NORMAL,
            value = "1",
            toggle = toggle,
            conversionWindow = listOf(window),
        ).send()
    }

    private suspend fun experimentToggles(): List<Toggle> =
        inventory.getAllTogglesForParent(PRIVACY_PRO_FEATURE_NAME).filter { it.featureName().name in EXPERIMENT_NAMES }

    private companion object {
        const val PRIVACY_PRO_FEATURE_NAME = "privacyPro"
        const val FREE_TRIALS_EXPERIMENT = "subscriptionOnboardingFreeTrialsOct2026"
        const val PAID_SUBS_EXPERIMENT = "subscriptionOnboardingPaidSubsOct2026"
        val EXPERIMENT_NAMES = setOf(FREE_TRIALS_EXPERIMENT, PAID_SUBS_EXPERIMENT)
    }
}
