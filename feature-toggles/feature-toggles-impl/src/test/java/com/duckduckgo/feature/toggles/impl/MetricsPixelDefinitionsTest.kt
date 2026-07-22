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
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.codegen.TestTriggerFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@SuppressLint("DenyListedApi")
class MetricsPixelDefinitionsTest {

    private lateinit var testFeature: TestTriggerFeature

    @Before
    fun setup() {
        testFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "testFeature",
        ).build().create(TestTriggerFeature::class.java)

        val enrollmentDate = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDate),
            ),
        )
    }

    @Test
    fun `returns empty list when no cohort assigned`() {
        testFeature.experimentFooFeature().setRawStoredState(
            State(remoteEnableState = true, enable = true, assignedCohort = null),
        )
        assertTrue(metricsPixel().getPixelDefinitions().isEmpty())
    }

    @Test
    fun `returns empty list when cohort name is empty`() {
        val enrollmentDate = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = "", weight = 1, enrollmentDateET = enrollmentDate),
            ),
        )
        assertTrue(metricsPixel().getPixelDefinitions().isEmpty())
    }

    @Test
    fun `returns empty list when enrollment date is null`() {
        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = State.Cohort(name = "control", weight = 1, enrollmentDateET = null),
            ),
        )
        assertTrue(metricsPixel().getPixelDefinitions().isEmpty())
    }

    @Test
    fun `pixel name contains feature name and cohort name`() {
        val definitions = metricsPixel().getPixelDefinitions()
        assertEquals("experiment_metrics_experimentFooFeature_control", definitions.first().pixelName)
    }

    @Test
    fun `params contain correct metric and value`() {
        val params = metricsPixel(metric = "my_metric", value = "42").getPixelDefinitions().first().params
        assertEquals("my_metric", params["metric"])
        assertEquals("42", params["value"])
    }

    @Test
    fun `params contain enrollment date formatted as ISO local date`() {
        val params = metricsPixel().getPixelDefinitions().first().params
        assertTrue(params["enrollmentDate"]!!.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun `conversionWindowDays is single value when lower equals upper`() {
        val params = metricsPixel(lowerWindow = 5, upperWindow = 5).getPixelDefinitions().first().params
        assertEquals("5", params["conversionWindowDays"])
    }

    @Test
    fun `conversionWindowDays is range when lower differs from upper`() {
        val params = metricsPixel(lowerWindow = 3, upperWindow = 7).getPixelDefinitions().first().params
        assertEquals("3-7", params["conversionWindowDays"])
    }

    @Test
    fun `returns one definition per conversion window`() {
        val pixel = MetricsPixel(
            metric = "m",
            value = "1",
            toggle = testFeature.experimentFooFeature(),
            conversionWindow = listOf(ConversionWindow(0, 1), ConversionWindow(2, 3), ConversionWindow(4, 5)),
        )
        assertEquals(3, pixel.getPixelDefinitions().size)
    }

    @Test
    fun `each definition has correct conversionWindowDays for its window`() {
        val pixel = MetricsPixel(
            metric = "m",
            value = "1",
            toggle = testFeature.experimentFooFeature(),
            conversionWindow = listOf(ConversionWindow(0, 1), ConversionWindow(7, 7)),
        )
        val definitions = pixel.getPixelDefinitions()
        assertEquals("0-1", definitions[0].params["conversionWindowDays"])
        assertEquals("7", definitions[1].params["conversionWindowDays"])
    }

    private fun metricsPixel(
        metric: String = "test_metric",
        value: String = "1",
        lowerWindow: Int = 0,
        upperWindow: Int = 1,
    ) = MetricsPixel(
        metric = metric,
        value = value,
        toggle = testFeature.experimentFooFeature(),
        conversionWindow = listOf(ConversionWindow(lowerWindow, upperWindow)),
    )
}
