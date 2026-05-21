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

package com.duckduckgo.app.onboardingquicksetup

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.send
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class OnboardingQuickSetupSearchMetricsAtbLifecyclePlugin @Inject constructor(
    private val toggles: OnboardingQuickSetupToggles,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : AtbLifecyclePlugin {

    override fun onSearchRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        appCoroutineScope.launch {
            buildMetrics().forEach { it.send() }
        }
    }

    private suspend fun buildMetrics(): List<MetricsPixel> {
        val toggle = toggles.onboardingQuickSetupExperimentMay26()
        if (!toggle.isEnabled() || !toggle.isEnrolled()) {
            return emptyList()
        }

        val d0toD7Daily = (0..7).map { ConversionWindow(lowerWindow = it, upperWindow = it) }

        return listOf(
            MetricsPixel(
                metric = "search",
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "1",
                toggle = toggle,
                conversionWindow = listOf(ConversionWindow(lowerWindow = 1, upperWindow = 3)),
            ),
            MetricsPixel(
                metric = "search",
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "2",
                toggle = toggle,
                conversionWindow = d0toD7Daily,
            ),
            MetricsPixel(
                metric = "search",
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "3",
                toggle = toggle,
                conversionWindow = d0toD7Daily,
            ),
            MetricsPixel(
                metric = "search",
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "4",
                toggle = toggle,
                conversionWindow = d0toD7Daily,
            ),
            MetricsPixel(
                metric = "search",
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "5",
                toggle = toggle,
                conversionWindow = d0toD7Daily,
            ),
            MetricsPixel(
                metric = "search",
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "6",
                toggle = toggle,
                conversionWindow = d0toD7Daily,
            ),
            MetricsPixel(
                metric = "search",
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "7",
                toggle = toggle,
                conversionWindow = d0toD7Daily,
            ),
            MetricsPixel(
                metric = "search",
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "8",
                toggle = toggle,
                conversionWindow = d0toD7Daily,
            ),
            MetricsPixel(
                metric = "search",
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "9",
                toggle = toggle,
                conversionWindow = d0toD7Daily,
            ),
            MetricsPixel(
                metric = "search",
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "10",
                toggle = toggle,
                conversionWindow = d0toD7Daily,
            ),
        )
    }
}
