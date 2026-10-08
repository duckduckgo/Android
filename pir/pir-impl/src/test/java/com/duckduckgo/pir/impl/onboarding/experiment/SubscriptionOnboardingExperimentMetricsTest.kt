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

import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeMetricsPixelExtension
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.FeatureName
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SubscriptionOnboardingExperimentMetricsTest {

    private val fakeMetricsPixelExtension = FakeMetricsPixelExtension()
    private val inventory: FeatureTogglesInventory = mock()
    private val freeTrialToggle: Toggle = mock {
        on { featureName() } doReturn FeatureName(parentName = "privacyPro", name = "subscriptionOnboardingFreeTrialsOct2026")
    }
    private val paidToggle: Toggle = mock {
        on { featureName() } doReturn FeatureName(parentName = "privacyPro", name = "subscriptionOnboardingPaidSubsOct2026")
    }
    private lateinit var testee: SubscriptionOnboardingExperimentMetrics

    @Before
    fun setup() {
        fakeMetricsPixelExtension.register()
        testee = RealSubscriptionOnboardingExperimentMetrics(inventory = inventory)
    }

    @Test
    fun whenPirActivatedThenFiresD1AndExperimentSpecificLaterWindowForEachExperiment() = runTest {
        whenever(inventory.getAllTogglesForParent("privacyPro")).thenReturn(listOf(freeTrialToggle, paidToggle))

        testee.firePirActivated()

        val sent = fakeMetricsPixelExtension.sentMetrics
        assertEquals(4, sent.size)
        sent.forEach {
            assertEquals("1", it.value)
            assertEquals(MetricType.NORMAL, it.type)
        }
        assertEquals(
            setOf(
                Triple("subscriptionOnboardingFreeTrialsOct2026", "pirActivated_d1", ConversionWindow(0, 1)),
                Triple("subscriptionOnboardingFreeTrialsOct2026", "pirActivated_d2_7", ConversionWindow(2, 7)),
                Triple("subscriptionOnboardingPaidSubsOct2026", "pirActivated_d1", ConversionWindow(0, 1)),
                Triple("subscriptionOnboardingPaidSubsOct2026", "pirActivated_d2_30", ConversionWindow(2, 30)),
            ),
            sent.map { Triple(it.toggle.featureName().name, it.metric, it.conversionWindow.single()) }.toSet(),
        )
    }

    @Test
    fun whenNoExperimentTogglesInInventoryThenNoMetricSent() = runTest {
        whenever(inventory.getAllTogglesForParent("privacyPro")).thenReturn(emptyList())

        testee.firePirActivated()

        assertTrue(fakeMetricsPixelExtension.sentMetrics.isEmpty())
    }
}
