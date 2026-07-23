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
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeMetricsPixelExtension
import com.duckduckgo.feature.toggles.api.MetricType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@SuppressLint("DenyListedApi")
class OnboardingPromptsExperimentMetricsTest {

    private val fakeMetricsPixelExtension = FakeMetricsPixelExtension()
    private val toggles = FakeFeatureToggleFactory.create(OnboardingPromptsToggles::class.java)
    private lateinit var metrics: OnboardingPromptsExperimentMetrics

    @Before
    fun setup() {
        fakeMetricsPixelExtension.register()
        metrics = OnboardingPromptsExperimentMetricsImpl(toggles = toggles)
    }

    @Test
    fun `when fireWidgetAddedMetric then sends widget_added day-0 NORMAL metric bound to the experiment toggle`() = runTest {
        metrics.fireWidgetAddedMetric()

        val sent = fakeMetricsPixelExtension.sentMetrics.single()
        assertEquals("widget_added", sent.metric)
        assertEquals("1", sent.value)
        assertEquals(MetricType.NORMAL, sent.type)
        assertEquals(listOf(ConversionWindow(lowerWindow = 0, upperWindow = 0)), sent.conversionWindow)
        assertEquals(
            toggles.addToDockAndWidgetExperimentJul25().featureName().name,
            sent.toggle.featureName().name,
        )
    }

    @Test
    fun `when fireWidgetSearchMetric then sends widget_search day5-7 NORMAL metric bound to the experiment toggle`() = runTest {
        metrics.fireWidgetSearchMetric()

        val sent = fakeMetricsPixelExtension.sentMetrics.single()
        assertEquals("widget_search", sent.metric)
        assertEquals("1", sent.value)
        assertEquals(MetricType.NORMAL, sent.type)
        assertEquals(listOf(ConversionWindow(lowerWindow = 5, upperWindow = 7)), sent.conversionWindow)
        assertEquals(
            toggles.addToDockAndWidgetExperimentJul25().featureName().name,
            sent.toggle.featureName().name,
        )
    }

    @Test
    fun `when fireOnboardingCompletedMetric then sends onboarding_completed day-0 NORMAL metric bound to the experiment toggle`() = runTest {
        metrics.fireOnboardingCompletedMetric()

        val sent = fakeMetricsPixelExtension.sentMetrics.single()
        assertEquals("onboarding_completed", sent.metric)
        assertEquals("1", sent.value)
        assertEquals(MetricType.NORMAL, sent.type)
        assertEquals(listOf(ConversionWindow(lowerWindow = 0, upperWindow = 0)), sent.conversionWindow)
        assertEquals(
            toggles.addToDockAndWidgetExperimentJul25().featureName().name,
            sent.toggle.featureName().name,
        )
    }
}
