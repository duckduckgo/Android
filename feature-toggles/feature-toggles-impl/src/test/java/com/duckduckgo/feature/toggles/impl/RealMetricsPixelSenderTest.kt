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

package com.duckduckgo.feature.toggles.impl

import android.annotation.SuppressLint
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.codegen.TestTriggerFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@SuppressLint("DenyListedApi")
class RealMetricsPixelSenderTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val store = FakeStore()
    private val fakePixel = FakeSenderPixel()
    private lateinit var testFeature: TestTriggerFeature
    private lateinit var sender: RealMetricsPixelSender

    @Before
    fun setup() {
        testFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "testFeature",
        ).build().create(TestTriggerFeature::class.java)

        sender = RealMetricsPixelSender(fakePixel, store, coroutineRule.testScope, coroutineRule.testDispatcherProvider)

        val enrollmentDate = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDate),
            ),
        )
    }

    // NORMAL type tests

    @Test
    fun `NORMAL - pixel fired when in conversion window`() = runTest {
        sender.send(metricsPixel(type = MetricType.NORMAL, lowerWindow = 0, upperWindow = 1))
        assertEquals(1, fakePixel.firedPixels.size)
    }

    @Test
    fun `NORMAL - pixel not fired when outside conversion window`() = runTest {
        sender.send(metricsPixel(type = MetricType.NORMAL, lowerWindow = 5, upperWindow = 7))
        assertTrue(fakePixel.firedPixels.isEmpty())
    }

    @Test
    fun `NORMAL - pixel not fired twice`() = runTest {
        val pixel = metricsPixel(type = MetricType.NORMAL, lowerWindow = 0, upperWindow = 1)
        sender.send(pixel)
        sender.send(pixel)
        assertEquals(1, fakePixel.firedPixels.size)
    }

    @Test
    fun `NORMAL - returns false when no cohort assigned`() = runTest {
        testFeature.experimentFooFeature().setRawStoredState(
            State(remoteEnableState = true, enable = true, assignedCohort = null),
        )
        val result = sender.send(metricsPixel(type = MetricType.NORMAL, lowerWindow = 0, upperWindow = 1))
        assertFalse(result)
        assertTrue(fakePixel.firedPixels.isEmpty())
    }

    @Test
    fun `NORMAL - returns true when cohort assigned`() = runTest {
        val result = sender.send(metricsPixel(type = MetricType.NORMAL, lowerWindow = 0, upperWindow = 1))
        assertTrue(result)
    }

    // COUNT_WHEN_IN_WINDOW type tests

    @Test
    fun `COUNT_WHEN_IN_WINDOW - pixel not fired before threshold reached`() = runTest {
        val pixel = metricsPixel(type = MetricType.COUNT_WHEN_IN_WINDOW, value = "3", lowerWindow = 0, upperWindow = 1)
        sender.send(pixel)
        sender.send(pixel)
        assertTrue(fakePixel.firedPixels.isEmpty())
    }

    @Test
    fun `COUNT_WHEN_IN_WINDOW - pixel fired when threshold reached`() = runTest {
        val pixel = metricsPixel(type = MetricType.COUNT_WHEN_IN_WINDOW, value = "3", lowerWindow = 0, upperWindow = 1)
        repeat(3) { sender.send(pixel) }
        assertEquals(1, fakePixel.firedPixels.size)
    }

    @Test
    fun `COUNT_WHEN_IN_WINDOW - pixel not fired again after threshold`() = runTest {
        val pixel = metricsPixel(type = MetricType.COUNT_WHEN_IN_WINDOW, value = "3", lowerWindow = 0, upperWindow = 1)
        repeat(5) { sender.send(pixel) }
        assertEquals(1, fakePixel.firedPixels.size)
    }

    @Test
    fun `COUNT_WHEN_IN_WINDOW - pixel not fired when outside conversion window`() = runTest {
        val pixel = metricsPixel(type = MetricType.COUNT_WHEN_IN_WINDOW, value = "1", lowerWindow = 5, upperWindow = 7)
        sender.send(pixel)
        assertTrue(fakePixel.firedPixels.isEmpty())
    }

    @Test
    fun `COUNT_WHEN_IN_WINDOW - count not incremented when outside conversion window`() = runTest {
        val pixel = metricsPixel(type = MetricType.COUNT_WHEN_IN_WINDOW, value = "1", lowerWindow = 5, upperWindow = 7)
        repeat(3) { sender.send(pixel) }
        val definition = pixel.getPixelDefinitions().first()
        assertEquals(0, store.getMetricForPixelDefinition(definition))
    }

    private fun metricsPixel(
        type: MetricType = MetricType.NORMAL,
        value: String = "1",
        lowerWindow: Int = 0,
        upperWindow: Int = 1,
    ) = MetricsPixel(
        metric = "test_metric",
        value = value,
        toggle = testFeature.experimentFooFeature(),
        conversionWindow = listOf(ConversionWindow(lowerWindow, upperWindow)),
        type = type,
    )
}

private class FakeSenderPixel : Pixel {
    val firedPixels = mutableListOf<String>()

    override fun fire(pixel: PixelName, parameters: Map<String, String>, encodedParameters: Map<String, String>, type: PixelType) {
        firedPixels.add(pixel.pixelName)
    }

    override fun fire(pixelName: String, parameters: Map<String, String>, encodedParameters: Map<String, String>, type: PixelType) {
        firedPixels.add(pixelName)
    }

    override fun enqueueFire(pixel: PixelName, parameters: Map<String, String>, encodedParameters: Map<String, String>, type: PixelType) = Unit
    override fun enqueueFire(pixelName: String, parameters: Map<String, String>, encodedParameters: Map<String, String>, type: PixelType) = Unit
}
