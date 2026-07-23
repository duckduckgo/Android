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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.send
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface OnboardingPromptsExperimentMetrics {

    suspend fun fireWidgetAddedMetric()

    suspend fun fireWidgetSearchMetric()

    suspend fun fireOnboardingCompletedMetric()
}

@ContributesBinding(AppScope::class)
class OnboardingPromptsExperimentMetricsImpl @Inject constructor(
    private val toggles: OnboardingPromptsToggles,
) : OnboardingPromptsExperimentMetrics {

    override suspend fun fireWidgetAddedMetric() {
        MetricsPixel(
            metric = "widget_added",
            type = MetricType.NORMAL,
            value = "1",
            toggle = toggles.addToDockAndWidgetExperimentJul25(),
            conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 0)),
        ).send()
    }

    override suspend fun fireWidgetSearchMetric() {
        MetricsPixel(
            metric = "widget_search",
            type = MetricType.NORMAL,
            value = "1",
            toggle = toggles.addToDockAndWidgetExperimentJul25(),
            conversionWindow = listOf(ConversionWindow(lowerWindow = 5, upperWindow = 7)),
        ).send()
    }

    override suspend fun fireOnboardingCompletedMetric() {
        MetricsPixel(
            metric = "onboarding_completed",
            type = MetricType.NORMAL,
            value = "1",
            toggle = toggles.addToDockAndWidgetExperimentJul25(),
            conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 0)),
        ).send()
    }
}
