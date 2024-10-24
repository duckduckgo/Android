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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoInteractions

class RetentionMetricsAtbLifecyclePluginTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val store = FakeStore()
    private lateinit var testFeature: TestTriggerFeature
    private lateinit var pluginPoint: FakePluginPoint
    private lateinit var inventory: FeatureTogglesInventory
    private lateinit var atbLifecyclePlugin: RetentionMetricsAtbLifecyclePlugin
    private val pixel: Pixel = mock()

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

        pluginPoint = FakePluginPoint(testFeature)
        atbLifecyclePlugin = RetentionMetricsAtbLifecyclePlugin(
            searchMetricPixelsPlugin = SearchMetricPixelsPlugin(inventory),
            appUseMetricPixelsPlugin = AppUseMetricPixelsPlugin(inventory),
            inventory = inventory,
            store = store,
            pixel = pixel,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun `when search atb refreshed and matches metric, pixel sent to all active experiments`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).truncatedTo(ChronoUnit.DAYS).toString()
        val enrollmentDateParsedET: String = ZonedDateTime.parse(enrollmentDateET).format(DateTimeFormatter.ISO_LOCAL_DATE).toString()

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        testFeature.fooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )

        atbLifecyclePlugin.onSearchRetentionAtbRefreshed("", "")

        verify(pixel).fire(
            "experiment_metrics_experimentFooFeature_control",
            mapOf(
                "metric" to "search",
                "value" to "1",
                "enrollmentDate" to enrollmentDateParsedET,
                "conversionWindowDays" to "0",
            ),
        )
        verify(pixel).fire(
            "experiment_metrics_fooFeature_control",
            mapOf(
                "metric" to "search",
                "value" to "1",
                "enrollmentDate" to enrollmentDateParsedET,
                "conversionWindowDays" to "0",
            ),
        )
    }

    @Test
    fun `when app use atb refreshed and matches metric, pixel sent to all active experiments`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).minusDays(1).truncatedTo(ChronoUnit.DAYS).toString()
        val enrollmentDateParsedET: String = ZonedDateTime.parse(enrollmentDateET).format(DateTimeFormatter.ISO_LOCAL_DATE).toString()

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        testFeature.fooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )

        atbLifecyclePlugin.onAppRetentionAtbRefreshed("", "")

        verify(pixel).fire(
            "experiment_metrics_experimentFooFeature_control",
            mapOf(
                "metric" to "app_use",
                "value" to "1",
                "enrollmentDate" to enrollmentDateParsedET,
                "conversionWindowDays" to "1",
            ),
        )
        verify(pixel).fire(
            "experiment_metrics_fooFeature_control",
            mapOf(
                "metric" to "app_use",
                "value" to "1",
                "enrollmentDate" to enrollmentDateParsedET,
                "conversionWindowDays" to "1",
            ),
        )
    }

    @Test
    fun `when search atb refreshed and does not match metric, pixel not sent`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).minusDays(10).truncatedTo(ChronoUnit.DAYS).toString()

        repeat(2) { store.increaseSearchForFeature("experimentFooFeature") }
        repeat(2) { store.increaseSearchForFeature("fooFeature") }

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        testFeature.fooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )

        atbLifecyclePlugin.onSearchRetentionAtbRefreshed("", "")

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when app use atb refreshed and does not match metric, pixel not sent`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).truncatedTo(ChronoUnit.DAYS).toString()

        repeat(2) { store.increaseAppUseForFeature("experimentFooFeature") }
        repeat(2) { store.increaseAppUseForFeature("fooFeature") }

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        testFeature.fooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )

        atbLifecyclePlugin.onAppRetentionAtbRefreshed("", "")

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when search atb refreshed and some metrics match, send pixels`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).minusDays(10).truncatedTo(ChronoUnit.DAYS).toString()
        val enrollmentDateParsedET: String = ZonedDateTime.parse(enrollmentDateET).format(DateTimeFormatter.ISO_LOCAL_DATE).toString()

        repeat(3) { store.increaseSearchForFeature("experimentFooFeature") }

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        testFeature.fooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )

        atbLifecyclePlugin.onSearchRetentionAtbRefreshed("", "")

        verify(pixel, times(0)).fire(
            "experiment_metrics_experimentFooFeature_control",
            mapOf(
                "metric" to "search",
                "value" to "1",
                "enrollmentDate" to enrollmentDateParsedET,
                "conversionWindowDays" to "0",
            ),
        )
        verify(pixel).fire(
            "experiment_metrics_fooFeature_control",
            mapOf(
                "metric" to "search",
                "value" to "1",
                "enrollmentDate" to enrollmentDateParsedET,
                "conversionWindowDays" to "0",
            ),
        )
    }

    @Test
    fun `when app use atb refreshed and some metrics match, send pixels`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).truncatedTo(ChronoUnit.DAYS).toString()
        val enrollmentDateParsedET: String = ZonedDateTime.parse(enrollmentDateET).format(DateTimeFormatter.ISO_LOCAL_DATE).toString()

        repeat(3) { store.increaseAppUseForFeature("experimentFooFeature") }

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        testFeature.fooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )

        atbLifecyclePlugin.onAppRetentionAtbRefreshed("", "")

        verify(pixel, times(0)).fire(
            "experiment_metrics_experimentFooFeature_control",
            mapOf(
                "metric" to "app_use",
                "value" to "1",
                "enrollmentDate" to enrollmentDateParsedET,
                "conversionWindowDays" to "1",
            ),
        )
        verify(pixel).fire(
            "experiment_metrics_fooFeature_control",
            mapOf(
                "metric" to "app_use",
                "value" to "1",
                "enrollmentDate" to enrollmentDateParsedET,
                "conversionWindowDays" to "1",
            ),
        )
    }
}

class FakeFeatureTogglesInventory(private val features: List<Toggle>) : FeatureTogglesInventory {
    override suspend fun getAll(): List<Toggle> {
        return features
    }
}
