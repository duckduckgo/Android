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

package com.duckduckgo.widget.experiment

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.widget.experiment.PostCtaExperienceExperimentImpl
import com.duckduckgo.app.widget.experiment.PostCtaExperiencePixelsPlugin
import com.duckduckgo.app.widget.experiment.PostCtaExperienceToggles
import com.duckduckgo.app.widget.experiment.PostCtaExperienceToggles.Cohorts
import com.duckduckgo.app.widget.experiment.store.WidgetSearchCountDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.PixelDefinition
import com.duckduckgo.feature.toggles.api.Toggle
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class PostCtaExperienceExperimentTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PostCtaExperienceExperimentImpl

    private val mockDispatcherProvider = coroutineRule.testDispatcherProvider
    private val mockToggle: Toggle = mock()
    private val mockPostCtaExperienceToggles: PostCtaExperienceToggles = mock()
    private val mockPostCtaExperiencePixelsPlugin: PostCtaExperiencePixelsPlugin = mock()
    private val mockPixel: Pixel = mock()
    private val mockWidgetSearchCountDataStore: WidgetSearchCountDataStore = mock()
    private val metricsPixel: MetricsPixel = mock()

    @Before
    fun setup() {
        whenever(mockPostCtaExperienceToggles.postCtaExperienceExperimentJun25()).thenReturn(mockToggle)

        testee = PostCtaExperienceExperimentImpl(
            dispatcherProvider = mockDispatcherProvider,
            postCtaExperienceToggles = mockPostCtaExperienceToggles,
            postCtaExperiencePixelsPlugin = mockPostCtaExperiencePixelsPlugin,
            pixel = mockPixel,
            widgetSearchCountDataStore = mockWidgetSearchCountDataStore,
        )
    }

    @Test
    fun `when enroll is called then experiment is enrolled`() = runTest {
        testee.enroll()

        verify(mockToggle).enroll()
    }

    @Test
    fun `when isControl is called then correct toggle is checked`() = runTest {
        whenever(mockToggle.isEnrolledAndEnabled(Cohorts.CONTROL)).thenReturn(true)

        val result = testee.isControl()

        assert(result)
        verify(mockToggle).isEnrolledAndEnabled(Cohorts.CONTROL)
    }

    @Test
    fun `when isSimpleSearchWidgetPrompt is called then correct toggle is checked`() = runTest {
        whenever(mockToggle.isEnrolledAndEnabled(Cohorts.VARIANT_SIMPLE_SEARCH_WIDGET_PROMPT)).thenReturn(true)

        val result = testee.isSimpleSearchWidgetPrompt()

        assert(result)
        verify(mockToggle).isEnrolledAndEnabled(Cohorts.VARIANT_SIMPLE_SEARCH_WIDGET_PROMPT)
    }

    @Test
    fun `when fireSettingsWidgetDisplay is called then corresponding pixel is fired`() = runTest {
        whenever(mockPostCtaExperiencePixelsPlugin.getSettingsWidgetDisplayMetric()).thenReturn(metricsPixel)
        val pixelDefinition = PixelDefinition("pixel_name", mapOf("key" to "value"))
        whenever(metricsPixel.getPixelDefinitions()).thenReturn(listOf(pixelDefinition))

        testee.fireSettingsWidgetDisplay()

        verify(mockPixel).fire(pixelDefinition.pixelName, pixelDefinition.params)
    }

    @Test
    fun `when fireWidgetSearchXCount and count reaches 3 then 3x pixel is fired`() = runTest {
        val pixelDefinitions = listOf(createPixelDefinitionWithValidWindow())
        whenever(mockPostCtaExperiencePixelsPlugin.getWidgetSearch3xMetric()).thenReturn(metricsPixel)
        whenever(mockPostCtaExperiencePixelsPlugin.getWidgetSearch5xMetric()).thenReturn(null)
        whenever(metricsPixel.getPixelDefinitions()).thenReturn(pixelDefinitions)
        whenever(mockWidgetSearchCountDataStore.getMetricForPixelDefinition(any())).thenReturn(2)
        whenever(mockWidgetSearchCountDataStore.increaseMetricForPixelDefinition(any())).thenReturn(3)

        testee.fireWidgetSearchXCount()

        verify(mockPixel).fire(pixelDefinitions[0].pixelName, pixelDefinitions[0].params)
    }

    @Test
    fun `when fireWidgetSearchXCount and count is already 3 then 3x pixel is not fired again`() = runTest {
        val pixelDefinitions = listOf(createPixelDefinitionWithValidWindow())
        whenever(mockPostCtaExperiencePixelsPlugin.getWidgetSearch3xMetric()).thenReturn(metricsPixel)
        whenever(mockPostCtaExperiencePixelsPlugin.getWidgetSearch5xMetric()).thenReturn(null)
        whenever(metricsPixel.getPixelDefinitions()).thenReturn(pixelDefinitions)
        whenever(mockWidgetSearchCountDataStore.getMetricForPixelDefinition(any())).thenReturn(3)

        testee.fireWidgetSearchXCount()

        verify(mockPixel, never()).fire(pixelDefinitions[0].pixelName, pixelDefinitions[0].params)
    }

    @Test
    fun `when fireWidgetSearchXCount and count reaches 5 then 5x pixel is fired`() = runTest {
        val pixelDefinitions = listOf(createPixelDefinitionWithValidWindow())
        whenever(mockPostCtaExperiencePixelsPlugin.getWidgetSearch3xMetric()).thenReturn(null)
        whenever(mockPostCtaExperiencePixelsPlugin.getWidgetSearch5xMetric()).thenReturn(metricsPixel)
        whenever(metricsPixel.getPixelDefinitions()).thenReturn(pixelDefinitions)
        whenever(mockWidgetSearchCountDataStore.getMetricForPixelDefinition(any())).thenReturn(4)
        whenever(mockWidgetSearchCountDataStore.increaseMetricForPixelDefinition(any())).thenReturn(5)

        testee.fireWidgetSearchXCount()

        verify(mockPixel).fire(pixelDefinitions[0].pixelName, pixelDefinitions[0].params)
    }

    @Test
    fun `when fireWidgetSearchXCount and count is already 5 then 5x pixel is not fired again`() = runTest {
        val pixelDefinitions = listOf(createPixelDefinitionWithValidWindow())
        whenever(mockPostCtaExperiencePixelsPlugin.getWidgetSearch3xMetric()).thenReturn(null)
        whenever(mockPostCtaExperiencePixelsPlugin.getWidgetSearch5xMetric()).thenReturn(metricsPixel)
        whenever(metricsPixel.getPixelDefinitions()).thenReturn(pixelDefinitions)
        whenever(mockWidgetSearchCountDataStore.getMetricForPixelDefinition(any())).thenReturn(5)

        testee.fireWidgetSearchXCount()

        verify(mockPixel, never()).fire(pixelDefinitions[0].pixelName, pixelDefinitions[0].params)
    }

    private fun createPixelDefinitionWithValidWindow(): PixelDefinition {
        val pastDate = LocalDate.now(ZoneId.of("America/New_York")).minusDays(5)
        return PixelDefinition(
            "pixel_name",
            mapOf(
                "enrollmentDate" to pastDate.toString(),
                "conversionWindowDays" to "5-7",
            ),
        )
    }
}
