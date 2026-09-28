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

import android.annotation.SuppressLint
import com.duckduckgo.app.onboarding.ui.page.configdriven.DownloadReasonSelection
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeMetricsPixelExtension
import com.duckduckgo.feature.toggles.api.MetricType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@SuppressLint("DenyListedApi")
class SegmentedOnboardingExperimentMetricsTest {

    private val fakeMetricsPixelExtension = FakeMetricsPixelExtension()
    private val toggles = FakeFeatureToggleFactory.create(SegmentedOnboardingFeatureToggles::class.java)
    private lateinit var metrics: SegmentedOnboardingExperimentMetrics

    @Before
    fun setup() {
        fakeMetricsPixelExtension.register()
        metrics = SegmentedOnboardingExperimentMetricsImpl(toggles = toggles)
    }

    @Test
    fun `when fireOnboardingCompletedMetric then sends onboarding_completed d0 NORMAL metric with an empty value`() = runTest {
        metrics.fireOnboardingCompletedMetric()

        val sent = fakeMetricsPixelExtension.sentMetrics.single()
        assertEquals("onboarding_completed", sent.metric)
        assertEquals("", sent.value)
        assertEquals(MetricType.NORMAL, sent.type)
        assertEquals(listOf(ConversionWindow(lowerWindow = 0, upperWindow = 0)), sent.conversionWindow)
        assertEquals(
            toggles.onboardingFlowByDownloadReasonExperiment().featureName().name,
            sent.toggle.featureName().name,
        )
    }

    @Test
    fun `when fireDownloadReasonSelectedMetric then the reason token is part of the metric name`() = runTest {
        val expected = mapOf(
            DownloadReasonSelection.SEARCH to "download_reason_selected_search",
            DownloadReasonSelection.AI_CHAT to "download_reason_selected_ai-chat",
            DownloadReasonSelection.NO_AI to "download_reason_selected_no-ai",
            DownloadReasonSelection.BLOCK_ADS to "download_reason_selected_ad-blocking",
        )

        assertEquals(DownloadReasonSelection.entries.toSet(), expected.keys)

        expected.forEach { (reason, metricName) ->
            fakeMetricsPixelExtension.sentMetrics.clear()

            metrics.fireDownloadReasonSelectedMetric(reason)

            val sent = fakeMetricsPixelExtension.sentMetrics.single()
            assertEquals(metricName, sent.metric)
            assertEquals("", sent.value)
            assertEquals(MetricType.NORMAL, sent.type)
            assertEquals(listOf(ConversionWindow(lowerWindow = 0, upperWindow = 0)), sent.conversionWindow)
        }
    }

    @Test
    fun `when fireDownloadReasonSearchRetentionMetrics then sends the six d5-7 thresholds for that reason`() = runTest {
        metrics.fireDownloadReasonSearchRetentionMetrics(DownloadReasonSelection.AI_CHAT)

        val sent = fakeMetricsPixelExtension.sentMetrics
        assertEquals(6, sent.size)
        assertEquals(listOf("1", "4", "6", "11", "21", "30"), sent.map { it.value })
        sent.forEach {
            assertEquals("download_reason_search_retention_ai-chat", it.metric)
            assertEquals(MetricType.COUNT_WHEN_IN_WINDOW, it.type)
            assertEquals(listOf(ConversionWindow(lowerWindow = 5, upperWindow = 7)), it.conversionWindow)
        }
    }
}
