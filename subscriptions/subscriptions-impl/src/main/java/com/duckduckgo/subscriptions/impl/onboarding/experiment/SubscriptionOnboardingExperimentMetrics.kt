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

package com.duckduckgo.subscriptions.impl.onboarding.experiment

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.send
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface SubscriptionOnboardingExperimentMetrics {

    /** A paid Duck.ai prompt was used. Fires regardless of where it happened (not onboarding-scoped). */
    suspend fun fireDuckAiPaidUsed()
}

@ContributesBinding(AppScope::class)
class RealSubscriptionOnboardingExperimentMetrics @Inject constructor(
    private val subscriptionsFeature: SubscriptionsFeature,
    private val dispatcherProvider: DispatcherProvider,
) : SubscriptionOnboardingExperimentMetrics {

    override suspend fun fireDuckAiPaidUsed() {
        withContext(dispatcherProvider.io()) {
            val freeTrial = subscriptionsFeature.subscriptionOnboardingFreeTrialsOct2026()
            fire(freeTrial, metric = "duckAiPaidUsed_d1", window = ConversionWindow(lowerWindow = 0, upperWindow = 1))
            fire(freeTrial, metric = "duckAiPaidUsed_d2_7", window = ConversionWindow(lowerWindow = 2, upperWindow = 7))

            val paid = subscriptionsFeature.subscriptionOnboardingPaidSubsOct2026()
            fire(paid, metric = "duckAiPaidUsed_d1", window = ConversionWindow(lowerWindow = 0, upperWindow = 1))
            fire(paid, metric = "duckAiPaidUsed_d2_30", window = ConversionWindow(lowerWindow = 2, upperWindow = 30))
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
}
