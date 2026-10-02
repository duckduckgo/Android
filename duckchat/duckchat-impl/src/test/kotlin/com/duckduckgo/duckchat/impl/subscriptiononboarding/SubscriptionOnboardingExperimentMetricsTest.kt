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

package com.duckduckgo.duckchat.impl.subscriptiononboarding

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
    fun whenAiFeaturesDisabledThenSendsMetricForBothExperimentToggles() = runTest {
        whenever(inventory.getAllTogglesForParent("privacyPro")).thenReturn(listOf(freeTrialToggle, paidToggle))

        testee.fireAiFeaturesDisabled()

        val sent = fakeMetricsPixelExtension.sentMetrics
        assertEquals(2, sent.size)
        sent.forEach {
            assertEquals("ai_features_disabled", it.metric)
            assertEquals("1", it.value)
            assertEquals(MetricType.NORMAL, it.type)
            assertEquals(listOf(ConversionWindow(lowerWindow = 0, upperWindow = 1)), it.conversionWindow)
        }
        assertEquals(setOf(freeTrialToggle, paidToggle), sent.map { it.toggle }.toSet())
    }

    @Test
    fun whenOnlyUnrelatedTogglesInInventoryThenNoMetricSent() = runTest {
        val unrelated: Toggle = mock {
            on { featureName() } doReturn FeatureName(parentName = "privacyPro", name = "someOtherToggle")
        }
        whenever(inventory.getAllTogglesForParent("privacyPro")).thenReturn(listOf(unrelated))

        testee.fireAiFeaturesDisabled()

        assertTrue(fakeMetricsPixelExtension.sentMetrics.isEmpty())
    }

    @Test
    fun whenNoTogglesInInventoryThenNoMetricSent() = runTest {
        whenever(inventory.getAllTogglesForParent("privacyPro")).thenReturn(emptyList())

        testee.fireAiFeaturesDisabled()

        assertTrue(fakeMetricsPixelExtension.sentMetrics.isEmpty())
    }
}
