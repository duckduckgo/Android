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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeMetricsPixelExtension
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SubscriptionOnboardingExperimentMetricsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val fakeMetricsPixelExtension = FakeMetricsPixelExtension()
    private val subscriptionsFeature: SubscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java)
    private lateinit var testee: SubscriptionOnboardingExperimentMetrics

    @Before
    fun setup() {
        fakeMetricsPixelExtension.register()
        testee = RealSubscriptionOnboardingExperimentMetrics(subscriptionsFeature, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenDuckAiPaidUsedThenFiresD1AndExperimentSpecificLaterWindowForEachExperiment() = runTest {
        testee.fireDuckAiPaidUsed()

        val freeTrialName = subscriptionsFeature.subscriptionOnboardingFreeTrialsOct2026().featureName().name
        val paidName = subscriptionsFeature.subscriptionOnboardingPaidSubsOct2026().featureName().name

        val sent = fakeMetricsPixelExtension.sentMetrics
        assertEquals(4, sent.size)
        sent.forEach {
            assertEquals("1", it.value)
            assertEquals(MetricType.NORMAL, it.type)
        }
        assertEquals(
            setOf(
                Triple(freeTrialName, "duckAiPaidUsed_d1", ConversionWindow(0, 1)),
                Triple(freeTrialName, "duckAiPaidUsed_d2_7", ConversionWindow(2, 7)),
                Triple(paidName, "duckAiPaidUsed_d1", ConversionWindow(0, 1)),
                Triple(paidName, "duckAiPaidUsed_d2_30", ConversionWindow(2, 30)),
            ),
            sent.map { Triple(it.toggle.featureName().name, it.metric, it.conversionWindow.single()) }.toSet(),
        )
    }
}
