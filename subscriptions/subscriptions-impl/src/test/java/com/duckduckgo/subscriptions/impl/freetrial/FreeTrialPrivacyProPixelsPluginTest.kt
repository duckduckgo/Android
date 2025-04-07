/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.freetrial

import android.annotation.SuppressLint
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.PixelDefinition
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class FreeTrialPrivacyProPixelsPluginTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val mockPixel: Pixel = mock()
    private val mockFreeTrialExperimentDataStore: FreeTrialExperimentDataStore = mock()

    private lateinit var testFeature: PrivacyProFeature
    private lateinit var testee: FreeTrialPrivacyProPixelsPlugin

    @Before
    fun setup() {
        testFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "testFeature",
        ).build().create(PrivacyProFeature::class.java)

        testFeature.privacyProFreeTrialJan25().setRawStoredState(state = State(enable = true))

        testee = FreeTrialPrivacyProPixelsPlugin(
            toggle = { testFeature },
            freeTrialExperimentDataStore = mockFreeTrialExperimentDataStore,
            pixel = mockPixel,
        )
    }

    @Test
    fun whenPaywallImpressionsIsZeroThenSetMetricsPixelValueAs0String() = runTest {
        val value = testee.getMetricsPixelValue(0)

        assertEquals(value, "0")
    }

    @Test
    fun whenPaywallImpressionsIsThreeThenSetMetricsPixelValueAs3String() = runTest {
        val value = testee.getMetricsPixelValue(3)

        assertEquals(value, "3")
    }

    @Test
    fun whenPaywallImpressionsIsFiveThenSetMetricsPixelValueAs5String() = runTest {
        val value = testee.getMetricsPixelValue(5)

        assertEquals(value, "5")
    }

    @Test
    fun whenPaywallImpressionsIsSixThenSetMetricsPixelValueWithCorrectValue() = runTest {
        val value = testee.getMetricsPixelValue(6)

        assertEquals(value, "6-10")
    }

    @Test
    fun whenPaywallImpressionsIsEightThenSetMetricsPixelValueWithCorrectValue() = runTest {
        val value = testee.getMetricsPixelValue(8)

        assertEquals(value, "6-10")
    }

    @Test
    fun whenPaywallImpressionsIsTenThenSetMetricsPixelValueWithCorrectValue() = runTest {
        val value = testee.getMetricsPixelValue(10)

        assertEquals(value, "6-10")
    }

    @Test
    fun whenPaywallImpressionsIsElevenThenSetMetricsPixelValueWithCorrectValue() = runTest {
        val value = testee.getMetricsPixelValue(11)

        assertEquals(value, "11-50")
    }

    @Test
    fun whenPaywallImpressionsIsThirtyThenSetMetricsPixelValueWithCorrectValue() = runTest {
        val value = testee.getMetricsPixelValue(30)

        assertEquals(value, "11-50")
    }

    @Test
    fun whenPaywallImpressionsIsFiftyThenSetMetricsPixelValueWithCorrectValue() = runTest {
        val value = testee.getMetricsPixelValue(50)

        assertEquals(value, "11-50")
    }

    @Test
    fun whenPaywallImpressionsIsGreaterThanFiftyThenSetMetricsPixelValueWithCorrectValue() = runTest {
        val value = testee.getMetricsPixelValue(60)

        assertEquals(value, "51+")
    }

    @Test
    fun givenMetricValueAlreadyFiredWhenFirePixelRequestedThenDoNotFireAgain() = runTest {
        val mockPixelDefinition: PixelDefinition = mock()
        whenever(mockFreeTrialExperimentDataStore.getMetricForPixelDefinition(mockPixelDefinition)).thenReturn("6-10")

        testee.firePixelFor(getMetricPixel("6-10"))
        verifyNoInteractions(mockPixel)
        verifyNoInteractions(mockFreeTrialExperimentDataStore)
    }

    private fun getMetricPixel(value: String): MetricsPixel {
        return MetricsPixel(
            metric = "metric",
            value = value,
            toggle = testFeature.privacyProFreeTrialJan25(),
            conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 3)),
        )
    }
}
