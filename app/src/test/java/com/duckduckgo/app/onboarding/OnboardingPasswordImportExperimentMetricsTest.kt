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
class OnboardingPasswordImportExperimentMetricsTest {

    private val fakeMetricsPixelExtension = FakeMetricsPixelExtension()
    private val toggles = FakeFeatureToggleFactory.create(OnboardingPasswordImportToggles::class.java)
    private lateinit var metrics: OnboardingPasswordImportExperimentMetrics

    @Before
    fun setup() {
        fakeMetricsPixelExtension.register()
        metrics = OnboardingPasswordImportExperimentMetricsImpl(toggles = toggles)
    }

    @Test
    fun `when fireOnboardingCompletedMetric then sends onboarding_completed d0 and d0-14 NORMAL metric for the experiment toggle`() = runTest {
        metrics.fireOnboardingCompletedMetric()

        val sent = fakeMetricsPixelExtension.sentMetrics.single()
        assertEquals("onboarding_completed", sent.metric)
        assertEquals("1", sent.value)
        assertEquals(MetricType.NORMAL, sent.type)
        assertEquals(
            listOf(
                ConversionWindow(lowerWindow = 0, upperWindow = 0),
                ConversionWindow(lowerWindow = 0, upperWindow = 14),
            ),
            sent.conversionWindow,
        )
        assertEquals(
            toggles.passwordImportExperimentAug25().featureName().name,
            sent.toggle.featureName().name,
        )
    }
}
