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

import com.duckduckgo.app.onboarding.ui.page.configdriven.DownloadReasonSelection
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.send
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SegmentedOnboardingExperimentMetrics {

    suspend fun fireOnboardingCompletedMetric()

    suspend fun fireDownloadReasonSelectedMetric(reason: DownloadReasonSelection)

    suspend fun fireDownloadReasonSearchRetentionMetrics(reason: DownloadReasonSelection)
}

@ContributesBinding(AppScope::class)
class SegmentedOnboardingExperimentMetricsImpl @Inject constructor(
    private val toggles: SegmentedOnboardingFeatureToggles,
) : SegmentedOnboardingExperimentMetrics {

    override suspend fun fireOnboardingCompletedMetric() {
        MetricsPixel(
            metric = "onboarding_completed",
            type = MetricType.NORMAL,
            value = "",
            toggle = toggles.onboardingFlowByDownloadReasonExperiment(),
            conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 0)),
        ).send()
    }

    override suspend fun fireDownloadReasonSelectedMetric(reason: DownloadReasonSelection) {
        MetricsPixel(
            metric = "download_reason_selected_${reason.pixelToken}",
            type = MetricType.NORMAL,
            value = "",
            toggle = toggles.onboardingFlowByDownloadReasonExperiment(),
            conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 0)),
        ).send()
    }

    override suspend fun fireDownloadReasonSearchRetentionMetrics(reason: DownloadReasonSelection) {
        val metric = "download_reason_search_retention_${reason.pixelToken}"
        val toggle = toggles.onboardingFlowByDownloadReasonExperiment()
        val window = listOf(ConversionWindow(lowerWindow = 5, upperWindow = 7))
        listOf(
            MetricsPixel(
                metric = metric,
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "1",
                toggle = toggle,
                conversionWindow = window,
            ),
            MetricsPixel(
                metric = metric,
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "4",
                toggle = toggle,
                conversionWindow = window,
            ),
            MetricsPixel(
                metric = metric,
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "6",
                toggle = toggle,
                conversionWindow = window,
            ),
            MetricsPixel(
                metric = metric,
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "11",
                toggle = toggle,
                conversionWindow = window,
            ),
            MetricsPixel(
                metric = metric,
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "21",
                toggle = toggle,
                conversionWindow = window,
            ),
            MetricsPixel(
                metric = metric,
                type = MetricType.COUNT_WHEN_IN_WINDOW,
                value = "30",
                toggle = toggle,
                conversionWindow = window,
            ),
        ).forEach { it.send() }
    }
}
