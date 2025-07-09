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

package com.duckduckgo.app.browser.defaultbrowsing.prompts.ui.experiment

import com.duckduckgo.app.statistics.pixels.Pixel
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
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class OnboardingHomeScreenWidgetExperimentTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val dispatcherProvider = coroutineRule.testDispatcherProvider

    private lateinit var testee: OnboardingHomeScreenWidgetExperimentImpl

    private val mockOnboardingHomeScreenWidgetToggles: OnboardingHomeScreenWidgetToggles = mock()
    private val mockOnboardingHomeScreenWidgetPixelsPlugin: OnboardingHomeScreenWidgetPixelsPlugin = mock()
    private val mockPixel: Pixel = mock()
    private val mockWidgetSearchCountDataStore: WidgetSearchCountDataStore = mock()
    private val mockToggle: Toggle = mock()
    private val mockMetricsPixel: MetricsPixel = mock()

    @Before
    fun setUp() {
        whenever(mockOnboardingHomeScreenWidgetToggles.onboardingHomeScreenWidgetExperimentJun25()).thenReturn(mockToggle)

        testee = OnboardingHomeScreenWidgetExperimentImpl(
            dispatcherProvider = dispatcherProvider,
            onboardingHomeScreenWidgetToggles = mockOnboardingHomeScreenWidgetToggles,
            onboardingHomeScreenWidgetPixelsPlugin = mockOnboardingHomeScreenWidgetPixelsPlugin,
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
        whenever(mockToggle.isEnrolledAndEnabled(OnboardingHomeScreenWidgetToggles.Cohorts.CONTROL)).thenReturn(true)

        val result = testee.isControl()

        assert(result)
        verify(mockToggle).isEnrolledAndEnabled(OnboardingHomeScreenWidgetToggles.Cohorts.CONTROL)
    }

    @Test
    fun `when isOnboardingHomeScreenWidgetExperiment is called then correct toggle is checked`() = runTest {
        whenever(mockToggle.isEnrolledAndEnabled(OnboardingHomeScreenWidgetToggles.Cohorts.VARIANT_ONBOARDING_HOME_SCREEN_WIDGET_PROMPT)).thenReturn(
            true,
        )

        val result = testee.isOnboardingHomeScreenWidgetExperiment()

        assert(result)
        verify(mockToggle).isEnrolledAndEnabled(OnboardingHomeScreenWidgetToggles.Cohorts.VARIANT_ONBOARDING_HOME_SCREEN_WIDGET_PROMPT)
    }

    @Test
    fun `when fireOnboardingWidgetDisplay is called then corresponding pixel is fired`() = runTest {
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getOnboardingWidgetDisplayMetric()).thenReturn(mockMetricsPixel)
        val pixelDefinition = PixelDefinition("pixel_name", mapOf("key" to "value"))
        whenever(mockMetricsPixel.getPixelDefinitions()).thenReturn(listOf(pixelDefinition))

        testee.fireOnboardingWidgetDisplay()

        verify(mockPixel).fire(pixelDefinition.pixelName, pixelDefinition.params)
    }

    @Test
    fun `when fireOnboardingWidgetAdd is called then corresponding pixel is fired`() = runTest {
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getOnboardingWidgetAddMetric()).thenReturn(mockMetricsPixel)
        val pixelDefinition = PixelDefinition("pixel_name", mapOf("key" to "value"))
        whenever(mockMetricsPixel.getPixelDefinitions()).thenReturn(listOf(pixelDefinition))

        testee.fireOnboardingWidgetAdd()

        verify(mockPixel).fire(pixelDefinition.pixelName, pixelDefinition.params)
    }

    @Test
    fun `when fireOnboardingWidgetDismiss is called then corresponding pixel is fired`() = runTest {
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getOnboardingWidgetDismissMetric()).thenReturn(mockMetricsPixel)
        val pixelDefinition = PixelDefinition("pixel_name", mapOf("key" to "value"))
        whenever(mockMetricsPixel.getPixelDefinitions()).thenReturn(listOf(pixelDefinition))

        testee.fireOnboardingWidgetDismiss()

        verify(mockPixel).fire(pixelDefinition.pixelName, pixelDefinition.params)
    }

    @Test
    fun `when fireWidgetSearch is called then corresponding pixel is fired`() = runTest {
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getWidgetSearchMetric()).thenReturn(mockMetricsPixel)
        val pixelDefinition = PixelDefinition("pixel_name", mapOf("key" to "value"))
        whenever(mockMetricsPixel.getPixelDefinitions()).thenReturn(listOf(pixelDefinition))

        testee.fireWidgetSearch()

        verify(mockPixel).fire(pixelDefinition.pixelName, pixelDefinition.params)
    }

    @Test
    fun `when fireWidgetSearchXCount and count reaches 3 then 3x pixel is fired`() = runTest {
        val pixelDefinition = createPixelDefinitionWithValidWindow()
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getWidgetSearch3xMetric()).thenReturn(mockMetricsPixel)
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getWidgetSearch5xMetric()).thenReturn(null)
        whenever(mockMetricsPixel.getPixelDefinitions()).thenReturn(listOf(pixelDefinition))
        whenever(mockWidgetSearchCountDataStore.getMetricForPixelDefinition(any())).thenReturn(2)
        whenever(mockWidgetSearchCountDataStore.increaseMetricForPixelDefinition(any())).thenReturn(3)

        testee.fireWidgetSearchXCount()

        verify(mockPixel).fire(pixelDefinition.pixelName, pixelDefinition.params)
    }

    @Test
    fun `when fireWidgetSearchXCount and count is already 3 then 3x pixel is not fired again`() = runTest {
        val pixelDefinition = createPixelDefinitionWithValidWindow()
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getWidgetSearch3xMetric()).thenReturn(mockMetricsPixel)
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getWidgetSearch5xMetric()).thenReturn(null)
        whenever(mockMetricsPixel.getPixelDefinitions()).thenReturn(listOf(pixelDefinition))
        whenever(mockWidgetSearchCountDataStore.getMetricForPixelDefinition(any())).thenReturn(3)

        testee.fireWidgetSearchXCount()

        verify(mockWidgetSearchCountDataStore, never()).increaseMetricForPixelDefinition(any())
        verify(mockPixel, never()).fire(pixelDefinition.pixelName, pixelDefinition.params)
    }

    @Test
    fun `when fireWidgetSearchXCount and count reaches 5 then 5x pixel is fired`() = runTest {
        val pixelDefinition = createPixelDefinitionWithValidWindow()
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getWidgetSearch3xMetric()).thenReturn(null)
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getWidgetSearch5xMetric()).thenReturn(mockMetricsPixel)
        whenever(mockMetricsPixel.getPixelDefinitions()).thenReturn(listOf(pixelDefinition))
        whenever(mockWidgetSearchCountDataStore.getMetricForPixelDefinition(any())).thenReturn(4)
        whenever(mockWidgetSearchCountDataStore.increaseMetricForPixelDefinition(any())).thenReturn(5)

        testee.fireWidgetSearchXCount()

        verify(mockPixel).fire(pixelDefinition.pixelName, pixelDefinition.params)
    }

    @Test
    fun `when fireWidgetSearchXCount and pixel is outside conversion window then metrics are not updated`() = runTest {
        val pixelDefinition = createPixelDefinitionWithInvalidWindow()
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getWidgetSearch3xMetric()).thenReturn(mockMetricsPixel)
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getWidgetSearch5xMetric()).thenReturn(null)
        whenever(mockMetricsPixel.getPixelDefinitions()).thenReturn(listOf(pixelDefinition))

        testee.fireWidgetSearchXCount()

        verify(mockWidgetSearchCountDataStore, never()).getMetricForPixelDefinition(any())
        verify(mockWidgetSearchCountDataStore, never()).increaseMetricForPixelDefinition(any())
        verify(mockPixel, never()).fire(pixelDefinition.pixelName, pixelDefinition.params)
    }

    @Test
    fun `when fireWidgetSearchXCount and count is less than threshold then pixel is not fired`() = runTest {
        val pixelDefinition = createPixelDefinitionWithValidWindow()
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getWidgetSearch3xMetric()).thenReturn(mockMetricsPixel)
        whenever(mockOnboardingHomeScreenWidgetPixelsPlugin.getWidgetSearch5xMetric()).thenReturn(null)
        whenever(mockMetricsPixel.getPixelDefinitions()).thenReturn(listOf(pixelDefinition))
        whenever(mockWidgetSearchCountDataStore.getMetricForPixelDefinition(any())).thenReturn(1)
        whenever(mockWidgetSearchCountDataStore.increaseMetricForPixelDefinition(any())).thenReturn(2)

        testee.fireWidgetSearchXCount()

        verify(mockWidgetSearchCountDataStore).increaseMetricForPixelDefinition(any())
        verify(mockPixel, never()).fire(pixelDefinition.pixelName, pixelDefinition.params)
    }

    private fun createPixelDefinitionWithValidWindow(): PixelDefinition {
        val today = LocalDate.now(ZoneId.of("America/New_York")).minusDays(5)
        return PixelDefinition(
            "pixel_name",
            mapOf(
                "enrollmentDate" to today.toString(),
                "conversionWindowDays" to "5-7",
            ),
        )
    }

    private fun createPixelDefinitionWithInvalidWindow(): PixelDefinition {
        val pastDate = LocalDate.now(ZoneId.of("America/New_York")).minusDays(20)
        return PixelDefinition(
            "pixel_name",
            mapOf(
                "enrollmentDate" to pastDate.toString(),
                "conversionWindowDays" to "5-7",
            ),
        )
    }
}
