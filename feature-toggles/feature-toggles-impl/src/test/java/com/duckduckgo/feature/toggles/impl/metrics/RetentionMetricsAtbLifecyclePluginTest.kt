/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.feature.toggles.impl.metrics

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.codegen.TestTriggerFeature
import com.duckduckgo.feature.toggles.impl.FakePluginPoint
import com.duckduckgo.feature.toggles.impl.FakeStore
import com.duckduckgo.feature.toggles.impl.RealFeatureTogglesInventory
import com.duckduckgo.feature.toggles.impl.RetentionMetric
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RetentionMetricsAtbLifecyclePluginTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val store = FakeStore()
    private lateinit var testFeature: TestTriggerFeature
    private lateinit var pluginPoint: FakePluginPoint
    private lateinit var inventory: FeatureTogglesInventory
    private lateinit var searchMetricPixelsPlugin: SearchMetricPixelsPlugin
    private lateinit var appUseMetricPixelsPlugin: AppUseMetricPixelsPlugin
    private lateinit var atbLifecyclePlugin: RetentionMetricsAtbLifecyclePlugin
    private val pixel = FakePixel()

    @Before
    fun setup() {
        testFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "testFeature",
        ).build().create(TestTriggerFeature::class.java)

        inventory = RealFeatureTogglesInventory(
            setOf(
                FakeFeatureTogglesInventory(
                    features = listOf(
                        testFeature.experimentFooFeature(),
                        testFeature.fooFeature(),
                    ),
                ),
            ),
            coroutineRule.testDispatcherProvider,
        )

        searchMetricPixelsPlugin = SearchMetricPixelsPlugin(inventory)
        appUseMetricPixelsPlugin = AppUseMetricPixelsPlugin(inventory)

        pluginPoint = FakePluginPoint(testFeature)
        atbLifecyclePlugin = RetentionMetricsAtbLifecyclePlugin(
            searchMetricPixelsPlugin = SearchMetricPixelsPlugin(inventory),
            appUseMetricPixelsPlugin = AppUseMetricPixelsPlugin(inventory),
            store = store,
            pixel = pixel,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun `when search atb refreshed and matches metric, pixel sent to all active experiments`() = runTest {
        setCohorts()

        atbLifecyclePlugin.onSearchRetentionAtbRefreshed("", "")

        searchMetricPixelsPlugin.getMetrics().forEach { metric ->
            metric.getPixelDefinitions().forEach { definition ->
                if (metric.value == "1") {
                    assertTrue(pixel.firedPixels.contains("${definition.pixelName}${definition.params}"))
                } else {
                    assertFalse(pixel.firedPixels.contains("${definition.pixelName}${definition.params}"))
                }
            }
        }
    }

    @Test
    fun `when app use atb refreshed and matches metric, pixel sent to all active experiments`() = runTest {
        setCohorts()

        atbLifecyclePlugin.onAppRetentionAtbRefreshed("", "")

        appUseMetricPixelsPlugin.getMetrics().forEach { metric ->
            metric.getPixelDefinitions().forEach { definition ->
                if (metric.value == "1") {
                    assertTrue(pixel.firedPixels.contains("${definition.pixelName}${definition.params}"))
                } else {
                    assertFalse(pixel.firedPixels.contains("${definition.pixelName}${definition.params}"))
                }
            }
        }
    }

    @Test
    fun `when search atb refreshed, fire all pixels which metric matches the number of searches done`() = runTest {
        setCohorts()

        searchMetricPixelsPlugin.getMetrics().forEach { metric ->
            metric.getPixelDefinitions().forEach { definition ->
                val tag = "${definition}_${RetentionMetric.SEARCH}"
                store.metrics[tag] = 3
            }
        }

        atbLifecyclePlugin.onSearchRetentionAtbRefreshed("", "")

        searchMetricPixelsPlugin.getMetrics().forEach { metric ->
            metric.getPixelDefinitions().forEach { definition ->
                if (metric.value == "4") {
                    assertTrue(pixel.firedPixels.contains("${definition.pixelName}${definition.params}"))
                } else {
                    assertFalse(pixel.firedPixels.contains("${definition.pixelName}${definition.params}"))
                }
            }
        }
    }

    @Test
    fun `when app use atb refreshed, fire all pixels which metric matches the number of app use`() = runTest {
        setCohorts()

        appUseMetricPixelsPlugin.getMetrics().forEach { metric ->
            metric.getPixelDefinitions().forEach { definition ->
                val tag = "${definition}_${RetentionMetric.APP_USE}"
                store.metrics[tag] = 3
            }
        }

        atbLifecyclePlugin.onAppRetentionAtbRefreshed("", "")

        appUseMetricPixelsPlugin.getMetrics().forEach { metric ->
            metric.getPixelDefinitions().forEach { definition ->
                if (metric.value == "4") {
                    assertTrue(pixel.firedPixels.contains("${definition.pixelName}${definition.params}"))
                } else {
                    assertFalse(pixel.firedPixels.contains("${definition.pixelName}${definition.params}"))
                }
            }
        }
    }

    @Test
    fun `when search atb refreshed, only fire pixels with active experiments`() = runTest {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York")).truncatedTo(ChronoUnit.DAYS).toString()
        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = false,
                enable = false,
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = today),
            ),
        )
        testFeature.fooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = today),
            ),
        )

        atbLifecyclePlugin.onSearchRetentionAtbRefreshed("", "")

        assertTrue(pixel.firedPixels.none { it.contains("experimentFooFeature") })
    }

    @Test
    fun `when search atb refreshed, only fire pixels from experiments with cohorts assigned`() = runTest {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York")).truncatedTo(ChronoUnit.DAYS).toString()
        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = null,
            ),
        )
        testFeature.fooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = today),
            ),
        )

        atbLifecyclePlugin.onSearchRetentionAtbRefreshed("", "")

        assertTrue(pixel.firedPixels.none { it.contains("experimentFooFeature") })
    }

    private fun setCohorts() {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York")).truncatedTo(ChronoUnit.DAYS).toString()
        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = today),
            ),
        )
        testFeature.fooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = today),
            ),
        )
    }
}

class FakeFeatureTogglesInventory(private val features: List<Toggle>) : FeatureTogglesInventory {
    override suspend fun getAll(): List<Toggle> {
        return features
    }
}

private class FakePixel : Pixel {

    val firedPixels = mutableListOf<String>()

    override fun fire(
        pixel: PixelName,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) {
        firedPixels.add(pixel.pixelName + parameters)
    }

    override fun fire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
        type: PixelType,
    ) {
        firedPixels.add(pixelName + parameters)
    }

    override fun enqueueFire(
        pixel: PixelName,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
    ) {
        firedPixels.add(pixel.pixelName + parameters)
    }

    override fun enqueueFire(
        pixelName: String,
        parameters: Map<String, String>,
        encodedParameters: Map<String, String>,
    ) {
        firedPixels.add(pixelName + parameters)
    }
}
