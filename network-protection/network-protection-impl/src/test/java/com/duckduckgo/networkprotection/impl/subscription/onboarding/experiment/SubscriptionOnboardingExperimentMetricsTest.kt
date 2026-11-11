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

package com.duckduckgo.networkprotection.impl.subscription.onboarding.experiment

import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
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
        testee = RealSubscriptionOnboardingExperimentMetrics(
            inventory = inventory,
            sharedPreferencesProvider = FakeSharedPreferencesProvider(),
        )
    }

    @Test
    fun whenVpnActivatedThenFiresD1AndExperimentSpecificLaterWindowForEachExperiment() = runTest {
        whenever(inventory.getAllActiveExperimentToggles()).thenReturn(listOf(freeTrialToggle, paidToggle))

        testee.fireVpnActivated()

        val sent = fakeMetricsPixelExtension.sentMetrics
        assertEquals(4, sent.size)
        sent.forEach {
            assertEquals("1", it.value)
            assertEquals(MetricType.NORMAL, it.type)
        }
        assertEquals(
            setOf(
                Triple("subscriptionOnboardingFreeTrialsOct2026", "vpnActivated_d1", ConversionWindow(0, 1)),
                Triple("subscriptionOnboardingFreeTrialsOct2026", "vpnActivated_d2_7", ConversionWindow(2, 7)),
                Triple("subscriptionOnboardingPaidSubsOct2026", "vpnActivated_d1", ConversionWindow(0, 1)),
                Triple("subscriptionOnboardingPaidSubsOct2026", "vpnActivated_d2_30", ConversionWindow(2, 30)),
            ),
            sent.map { Triple(it.toggle.featureName().name, it.metric, it.conversionWindow.single()) }.toSet(),
        )
    }

    @Test
    fun whenNoActiveExperimentsThenNoMetricSent() = runTest {
        whenever(inventory.getAllActiveExperimentToggles()).thenReturn(emptyList())

        testee.fireVpnActivated()

        assertTrue(fakeMetricsPixelExtension.sentMetrics.isEmpty())
    }

    @Test
    fun whenVpnActivatedAgainThenNoMetricSentTwice() = runTest {
        whenever(inventory.getAllActiveExperimentToggles()).thenReturn(listOf(freeTrialToggle))

        testee.fireVpnActivated()
        testee.fireVpnActivated()

        assertEquals(2, fakeMetricsPixelExtension.sentMetrics.size)
    }

    @Test
    fun whenOnlyOtherExperimentsActiveThenNoMetricSent() = runTest {
        val otherToggle: Toggle = mock {
            on { featureName() } doReturn FeatureName(parentName = "privacyPro", name = "otherExperiment")
        }
        whenever(inventory.getAllActiveExperimentToggles()).thenReturn(listOf(otherToggle))

        testee.fireVpnActivated()

        assertTrue(fakeMetricsPixelExtension.sentMetrics.isEmpty())
    }

    @Test
    fun whenVpnActivatedBeforeEnrollmentThenMetricSentOnceEnrolled() = runTest {
        whenever(inventory.getAllActiveExperimentToggles()).thenReturn(emptyList())
        testee.fireVpnActivated()

        whenever(inventory.getAllActiveExperimentToggles()).thenReturn(listOf(freeTrialToggle))
        testee.fireVpnActivated()

        assertEquals(2, fakeMetricsPixelExtension.sentMetrics.size)
    }
}
