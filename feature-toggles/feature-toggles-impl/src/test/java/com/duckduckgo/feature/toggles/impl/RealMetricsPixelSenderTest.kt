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

        sender = RealMetricsPixelSender(fakePixel, store, coroutineRule.testDispatcherProvider)

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
    fun `NORMAL - pixel not fired when no cohort assigned`() = runTest {
        testFeature.experimentFooFeature().setRawStoredState(
            State(remoteEnableState = true, enable = true, assignedCohort = null),
        )
        sender.send(metricsPixel(type = MetricType.NORMAL, lowerWindow = 0, upperWindow = 1))
        assertTrue(fakePixel.firedPixels.isEmpty())
    }

    @Test
    fun `NORMAL - pixel not fired on second send`() = runTest {
        val pixel = metricsPixel(type = MetricType.NORMAL, lowerWindow = 0, upperWindow = 1)
        sender.send(pixel)
        fakePixel.firedPixels.clear()
        sender.send(pixel)
        assertTrue(fakePixel.firedPixels.isEmpty())
    }

    @Test
    fun `NORMAL - all conversion windows are processed without short-circuiting`() = runTest {
        val pixel = MetricsPixel(
            metric = "test_metric",
            value = "1",
            toggle = testFeature.experimentFooFeature(),
            conversionWindow = listOf(ConversionWindow(0, 0), ConversionWindow(0, 1)),
            type = MetricType.NORMAL,
        )
        sender.send(pixel)
        assertEquals(2, fakePixel.firedPixels.size)
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

    @Test
    fun `COUNT_WHEN_IN_WINDOW - count incremented when in conversion window`() = runTest {
        val pixel = metricsPixel(type = MetricType.COUNT_WHEN_IN_WINDOW, value = "3", lowerWindow = 0, upperWindow = 1)
        repeat(2) { sender.send(pixel) }
        val definition = pixel.getPixelDefinitions().first()
        assertEquals(2, store.getMetricForPixelDefinition(definition))
    }

    @Test
    fun `COUNT_WHEN_IN_WINDOW - pixel not fired after single send below threshold`() = runTest {
        val pixel = metricsPixel(type = MetricType.COUNT_WHEN_IN_WINDOW, value = "3", lowerWindow = 0, upperWindow = 1)
        sender.send(pixel)
        assertTrue(fakePixel.firedPixels.isEmpty())
    }

    @Test
    fun `COUNT_WHEN_IN_WINDOW - pixel not fired after threshold already reached`() = runTest {
        val pixel = metricsPixel(type = MetricType.COUNT_WHEN_IN_WINDOW, value = "3", lowerWindow = 0, upperWindow = 1)
        repeat(3) { sender.send(pixel) }
        fakePixel.firedPixels.clear()
        sender.send(pixel)
        assertTrue(fakePixel.firedPixels.isEmpty())
    }

    @Test
    fun `COUNT_WHEN_IN_WINDOW - pixel not fired when no cohort assigned`() = runTest {
        testFeature.experimentFooFeature().setRawStoredState(
            State(remoteEnableState = true, enable = true, assignedCohort = null),
        )
        sender.send(metricsPixel(type = MetricType.COUNT_WHEN_IN_WINDOW, value = "1", lowerWindow = 0, upperWindow = 1))
        assertTrue(fakePixel.firedPixels.isEmpty())
    }

    // COUNT_ALWAYS type tests

    @Test
    fun `COUNT_ALWAYS - pixel not fired before threshold reached`() = runTest {
        val pixel = metricsPixel(type = MetricType.COUNT_ALWAYS, value = "3", lowerWindow = 0, upperWindow = 1)
        sender.send(pixel)
        sender.send(pixel)
        assertTrue(fakePixel.firedPixels.isEmpty())
    }

    @Test
    fun `COUNT_ALWAYS - pixel fired when threshold reached in window`() = runTest {
        val pixel = metricsPixel(type = MetricType.COUNT_ALWAYS, value = "3", lowerWindow = 0, upperWindow = 1)
        repeat(3) { sender.send(pixel) }
        assertEquals(1, fakePixel.firedPixels.size)
    }

    @Test
    fun `COUNT_ALWAYS - pixel not fired again after threshold`() = runTest {
        val pixel = metricsPixel(type = MetricType.COUNT_ALWAYS, value = "3", lowerWindow = 0, upperWindow = 1)
        repeat(5) { sender.send(pixel) }
        assertEquals(1, fakePixel.firedPixels.size)
    }

    @Test
    fun `COUNT_ALWAYS - count incremented even when outside window`() = runTest {
        val pixel = metricsPixel(type = MetricType.COUNT_ALWAYS, value = "3", lowerWindow = 5, upperWindow = 7)
        repeat(2) { sender.send(pixel) }
        val definition = pixel.getPixelDefinitions().first()
        assertEquals(2, store.getMetricForPixelDefinition(definition))
    }

    @Test
    fun `COUNT_ALWAYS - pixel not fired when threshold reached outside window`() = runTest {
        val pixel = metricsPixel(type = MetricType.COUNT_ALWAYS, value = "3", lowerWindow = 5, upperWindow = 7)
        repeat(3) { sender.send(pixel) }
        assertTrue(fakePixel.firedPixels.isEmpty())
    }

    @Test
    fun `COUNT_ALWAYS - pixel not fired when no cohort assigned`() = runTest {
        testFeature.experimentFooFeature().setRawStoredState(
            State(remoteEnableState = true, enable = true, assignedCohort = null),
        )
        sender.send(metricsPixel(type = MetricType.COUNT_ALWAYS, value = "1", lowerWindow = 0, upperWindow = 1))
        assertTrue(fakePixel.firedPixels.isEmpty())
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

private class FakeStore : MetricsPixelStore {
    private val firedTags = mutableSetOf<String>()
    private val metricCounts = mutableMapOf<PixelDefinition, Int>()

    override suspend fun wasPixelFired(tag: String) = firedTags.contains(tag)

    override fun storePixelTag(tag: String) {
        firedTags.add(tag)
    }

    override suspend fun increaseMetricForPixelDefinition(definition: PixelDefinition): Int {
        val count = (metricCounts.getOrDefault(definition, 0) + 1)
        metricCounts[definition] = count
        return count
    }

    override suspend fun getMetricForPixelDefinition(definition: PixelDefinition) = metricCounts.getOrDefault(definition, 0)
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
