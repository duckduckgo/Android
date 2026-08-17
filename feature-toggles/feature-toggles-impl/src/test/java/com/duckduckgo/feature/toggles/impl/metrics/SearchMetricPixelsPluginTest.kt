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

package com.duckduckgo.feature.toggles.impl.metrics

import android.annotation.SuppressLint
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.codegen.TestTriggerFeature
import com.duckduckgo.feature.toggles.impl.RealFeatureTogglesInventory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@SuppressLint("DenyListedApi")
class SearchMetricPixelsPluginTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val testFeature = FakeFeatureToggleFactory.create(TestTriggerFeature::class.java)

    private val plugin = SearchMetricPixelsPlugin(
        RealFeatureTogglesInventory(
            setOf(FakeFeatureTogglesInventory(listOf(testFeature.experimentFooFeature()))),
            coroutineRule.testDispatcherProvider,
        ),
    )

    @Test
    fun `when experiment enrolled, metrics are defined for the canonical value thresholds`() = runTest {
        enrollExperiment()

        val metrics = plugin.getMetrics()

        assertEquals(listOf("1", "4", "6", "11", "21", "30"), metrics.map { it.value })
        assertTrue(metrics.all { it.metric == "search" && it.type == MetricType.COUNT_WHEN_IN_WINDOW })
    }

    @Test
    fun `when experiment enrolled, value 1 keeps daily coverage plus the aggregate windows`() = runTest {
        enrollExperiment()

        val windows = plugin.getMetrics().single { it.value == "1" }.conversionWindow

        val expected = (0..14).map { ConversionWindow(lowerWindow = it, upperWindow = it) } +
            listOf(
                ConversionWindow(lowerWindow = 1, upperWindow = 4),
                ConversionWindow(lowerWindow = 5, upperWindow = 7),
                ConversionWindow(lowerWindow = 8, upperWindow = 14),
            )
        assertEquals(expected.size, windows.size)
        assertEquals(expected.toSet(), windows.toSet())
    }

    @Test
    fun `when experiment enrolled, count thresholds only use the aggregate windows`() = runTest {
        enrollExperiment()

        val thresholdMetrics = plugin.getMetrics().filter { it.value != "1" }

        val expected = listOf(
            ConversionWindow(lowerWindow = 5, upperWindow = 7),
            ConversionWindow(lowerWindow = 8, upperWindow = 14),
        )
        assertTrue(thresholdMetrics.all { it.conversionWindow == expected })
    }

    @Test
    fun `when experiment enrolled, no metric uses the pre-alignment 8 to 15 window`() = runTest {
        enrollExperiment()

        val windows = plugin.getMetrics().flatMap { it.conversionWindow }

        assertTrue(windows.none { it.upperWindow == 15 })
    }

    @Test
    fun `when no experiment enrolled, no metrics are defined`() = runTest {
        assertTrue(plugin.getMetrics().isEmpty())
    }

    private fun enrollExperiment() {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        val cohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = today)
        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                cohorts = listOf(cohort),
                assignedCohort = cohort,
            ),
        )
    }
}
