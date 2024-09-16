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

package com.duckduckgo.experiments.impl.loadingbarexperiment

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.experiments.api.loadingbarexperiment.LoadingBarExperimentManager
import com.duckduckgo.experiments.impl.loadingbarexperiment.LoadingBarExperimentPixels.LOADING_BAR_EXPERIMENT_ENROLLMENT_CONTROL
import com.duckduckgo.experiments.impl.loadingbarexperiment.LoadingBarExperimentPixels.LOADING_BAR_EXPERIMENT_ENROLLMENT_TEST
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

class LoadingBarExperimentVariantInitializerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: LoadingBarExperimentVariantInitializer

    private val mockLoadingBarExperimentManager: LoadingBarExperimentManager = mock()
    private val mockLoadingBarExperimentDataStore: LoadingBarExperimentDataStore = mock()
    private val mockLoadingBarExperimentFeature: LoadingBarExperimentFeature = mock()
    private val mockPixel: Pixel = mock()
    private val mockToggle: Toggle = mock()
    private val mockAllocationToggle: Toggle = mock()

    @Before
    fun setup() {
        testee = spy(
            LoadingBarExperimentVariantInitializer(
                mockLoadingBarExperimentManager,
                mockLoadingBarExperimentDataStore,
                mockLoadingBarExperimentFeature,
                mockPixel,
                coroutineRule.testScope,
                coroutineRule.testDispatcherProvider,
            ),
        )
        whenever(mockLoadingBarExperimentFeature.self()).thenReturn(mockToggle)
        whenever(mockLoadingBarExperimentFeature.allocateVariants()).thenReturn(mockAllocationToggle)
    }

    @Test
    fun whenPrivacyConfigDownloadedAndGeneratedBooleanIsTrueThenVariantSetToTrueAndTestPixelFired() = runTest {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(mockAllocationToggle.isEnabled()).thenReturn(true)
        whenever(mockLoadingBarExperimentDataStore.variant).thenReturn(true)
        whenever(testee.generateRandomBoolean()).thenReturn(true)

        testee.onPrivacyConfigDownloaded()

        verify(mockLoadingBarExperimentDataStore).variant = true
        verify(mockPixel).fire(LOADING_BAR_EXPERIMENT_ENROLLMENT_TEST)
    }

    @Test
    fun whenPrivacyConfigDownloadedAndGeneratedBooleanIsFalseThenVariantSetToFalseAndControlPixelFired() = runTest {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(mockAllocationToggle.isEnabled()).thenReturn(true)
        whenever(mockLoadingBarExperimentDataStore.variant).thenReturn(false)
        whenever(testee.generateRandomBoolean()).thenReturn(false)

        testee.onPrivacyConfigDownloaded()

        verify(mockLoadingBarExperimentDataStore).variant = false
        verify(mockPixel).fire(LOADING_BAR_EXPERIMENT_ENROLLMENT_CONTROL)
    }

    @Test
    fun whenPrivacyConfigDownloadedAndVariantAlreadySetThenNoVariantChangesAndNoPixelsFired() = runTest {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(true)

        testee.onPrivacyConfigDownloaded()

        verify(mockLoadingBarExperimentDataStore, never()).variant = any()
        verify(mockPixel, never()).fire(LOADING_BAR_EXPERIMENT_ENROLLMENT_TEST)
        verify(mockPixel, never()).fire(LOADING_BAR_EXPERIMENT_ENROLLMENT_CONTROL)
    }

    @Test
    fun whenPrivacyConfigDownloadedAndFeatureToggleDisabledThenNoVariantChangesAndNoPixelsFired() = runTest {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(false)

        testee.onPrivacyConfigDownloaded()

        verify(mockLoadingBarExperimentDataStore, never()).variant = any()
        verify(mockPixel, never()).fire(LOADING_BAR_EXPERIMENT_ENROLLMENT_TEST)
        verify(mockPixel, never()).fire(LOADING_BAR_EXPERIMENT_ENROLLMENT_CONTROL)
    }

    @Test
    fun whenPrivacyConfigDownloadedAndAllocationToggleDisabledThenNoVariantChangesAndNoPixelsFired() = runTest {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(true)
        whenever(mockAllocationToggle.isEnabled()).thenReturn(false)

        testee.onPrivacyConfigDownloaded()

        verify(mockLoadingBarExperimentDataStore, never()).variant = any()
        verify(mockPixel, never()).fire(LOADING_BAR_EXPERIMENT_ENROLLMENT_TEST)
        verify(mockPixel, never()).fire(LOADING_BAR_EXPERIMENT_ENROLLMENT_CONTROL)
    }

    @Test
    fun whenPrivacyConfigDownloadedAndBothTogglesDisabledThenNoVariantChangesAndNoPixelsFired() = runTest {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(false)
        whenever(mockAllocationToggle.isEnabled()).thenReturn(false)

        testee.onPrivacyConfigDownloaded()

        verify(mockLoadingBarExperimentDataStore, never()).variant = any()
        verify(mockPixel, never()).fire(LOADING_BAR_EXPERIMENT_ENROLLMENT_TEST)
        verify(mockPixel, never()).fire(LOADING_BAR_EXPERIMENT_ENROLLMENT_CONTROL)
    }

    @Test
    fun whenPrivacyConfigDownloadedThenLoadingBarExperimentManagerUpdated() = runTest {
        testee.onPrivacyConfigDownloaded()

        verify(mockLoadingBarExperimentManager).update()
    }
}
