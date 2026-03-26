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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeMetricsPixelExtension
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.codegen.TestTriggerFeature
import com.duckduckgo.feature.toggles.impl.RealFeatureTogglesInventory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class RetentionMetricsAtbLifecyclePluginTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val fakeMetricsPixelExtension = FakeMetricsPixelExtension()
    private lateinit var testFeature: TestTriggerFeature
    private lateinit var inventory: FeatureTogglesInventory
    private lateinit var searchMetricPixelsPlugin: SearchMetricPixelsPlugin
    private lateinit var appUseMetricPixelsPlugin: AppUseMetricPixelsPlugin
    private lateinit var atbLifecyclePlugin: RetentionMetricsAtbLifecyclePlugin

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

        fakeMetricsPixelExtension.register()

        searchMetricPixelsPlugin = SearchMetricPixelsPlugin(inventory)
        appUseMetricPixelsPlugin = AppUseMetricPixelsPlugin(inventory)

        atbLifecyclePlugin = RetentionMetricsAtbLifecyclePlugin(
            searchMetricPixelsPlugin = searchMetricPixelsPlugin,
            appUseMetricPixelsPlugin = appUseMetricPixelsPlugin,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun `when search atb refreshed and cohorts assigned, send is called for all search metrics`() = runTest {
        setCohorts(ZonedDateTime.now(ZoneId.of("America/New_York")).toString())

        atbLifecyclePlugin.onSearchRetentionAtbRefreshed("", "")

        val expected = searchMetricPixelsPlugin.getMetrics()
        assertEquals(expected.size, fakeMetricsPixelExtension.sentMetrics.size)
        assertTrue(fakeMetricsPixelExtension.sentMetrics.all { it.metric == "search" })
    }

    @Test
    fun `when app use atb refreshed and cohorts assigned, send is called for all app use metrics`() = runTest {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York")).minusDays(1).toString()
        setCohorts(today)

        atbLifecyclePlugin.onAppRetentionAtbRefreshed("", "")

        val expected = appUseMetricPixelsPlugin.getMetrics()
        assertEquals(expected.size, fakeMetricsPixelExtension.sentMetrics.size)
        assertTrue(fakeMetricsPixelExtension.sentMetrics.all { it.metric == "app_use" })
    }

    @Test
    fun `when search atb refreshed, only send metrics for active experiments`() = runTest {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = false,
                enable = false,
                cohorts = listOf(State.Cohort(name = "control", weight = 1, enrollmentDateET = today)),
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = today),
            ),
        )
        testFeature.fooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                cohorts = listOf(State.Cohort(name = "control", weight = 1, enrollmentDateET = today)),
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = today),
            ),
        )

        atbLifecyclePlugin.onSearchRetentionAtbRefreshed("", "")

        assertTrue(fakeMetricsPixelExtension.sentMetrics.none { it.toggle.featureName().name == "experimentFooFeature" })
        assertFalse(fakeMetricsPixelExtension.sentMetrics.none { it.toggle.featureName().name == "fooFeature" })
    }

    @Test
    fun `when search atb refreshed, only send metrics from experiments with cohorts assigned`() = runTest {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        testFeature.experimentFooFeature().setRawStoredState(
            State(remoteEnableState = true, enable = true, assignedCohort = null),
        )
        testFeature.fooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                cohorts = listOf(State.Cohort(name = "control", weight = 1, enrollmentDateET = today)),
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = today),
            ),
        )

        atbLifecyclePlugin.onSearchRetentionAtbRefreshed("", "")

        assertTrue(fakeMetricsPixelExtension.sentMetrics.none { it.toggle.featureName().name == "experimentFooFeature" })
        assertFalse(fakeMetricsPixelExtension.sentMetrics.none { it.toggle.featureName().name == "fooFeature" })
    }

    @Test
    fun `when app use atb refreshed, only send metrics for active experiments`() = runTest {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York")).minusDays(1).toString()
        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = false,
                enable = false,
                cohorts = listOf(State.Cohort(name = "control", weight = 1, enrollmentDateET = today)),
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = today),
            ),
        )
        testFeature.fooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                cohorts = listOf(State.Cohort(name = "control", weight = 1, enrollmentDateET = today)),
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = today),
            ),
        )

        atbLifecyclePlugin.onAppRetentionAtbRefreshed("", "")

        assertTrue(fakeMetricsPixelExtension.sentMetrics.none { it.toggle.featureName().name == "experimentFooFeature" })
        assertFalse(fakeMetricsPixelExtension.sentMetrics.none { it.toggle.featureName().name == "fooFeature" })
    }

    @Test
    fun `when app use atb refreshed, only send metrics from experiments with cohorts assigned`() = runTest {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York")).minusDays(1).toString()
        testFeature.experimentFooFeature().setRawStoredState(
            State(remoteEnableState = true, enable = true, assignedCohort = null),
        )
        testFeature.fooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                cohorts = listOf(State.Cohort(name = "control", weight = 1, enrollmentDateET = today)),
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = today),
            ),
        )

        atbLifecyclePlugin.onAppRetentionAtbRefreshed("", "")

        assertTrue(fakeMetricsPixelExtension.sentMetrics.none { it.toggle.featureName().name == "experimentFooFeature" })
        assertFalse(fakeMetricsPixelExtension.sentMetrics.none { it.toggle.featureName().name == "fooFeature" })
    }

    private suspend fun setCohorts(today: String) {
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
