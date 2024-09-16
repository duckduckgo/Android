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

import com.duckduckgo.experiments.impl.loadingbarexperiment.DuckDuckGoLoadingBarExperimentManager
import com.duckduckgo.experiments.impl.loadingbarexperiment.LoadingBarExperimentDataStore
import com.duckduckgo.experiments.impl.loadingbarexperiment.LoadingBarExperimentFeature
import com.duckduckgo.feature.toggles.api.Toggle
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class DuckDuckGoLoadingBarExperimentManagerTest {

    private lateinit var testee: DuckDuckGoLoadingBarExperimentManager

    private val mockLoadingBarExperimentDataStore: LoadingBarExperimentDataStore = mock()
    private val mockLoadingBarExperimentFeature: LoadingBarExperimentFeature = mock()
    private val mockToggle: Toggle = mock()

    @Before
    fun setup() {
        testee = DuckDuckGoLoadingBarExperimentManager(mockLoadingBarExperimentDataStore, mockLoadingBarExperimentFeature)
        whenever(mockLoadingBarExperimentFeature.self()).thenReturn(mockToggle)
    }

    @Test
    fun whenHasVariantAndIsEnabledThenIsExperimentEnabledReturnsTrue() {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(true)
        whenever(mockToggle.isEnabled()).thenReturn(true)

        assertTrue(testee.isExperimentEnabled())

        verify(mockLoadingBarExperimentDataStore).hasVariant
        verify(mockLoadingBarExperimentFeature.self()).isEnabled()
    }

    @Test
    fun whenHasNoVariantThenIsExperimentEnabledReturnsFalse() {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(true)

        assertFalse(testee.isExperimentEnabled())

        verify(mockLoadingBarExperimentDataStore).hasVariant
        verify(mockLoadingBarExperimentFeature.self()).isEnabled()
    }

    @Test
    fun whenIsNotEnabledThenIsExperimentEnabledReturnsFalse() {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(true)
        whenever(mockToggle.isEnabled()).thenReturn(false)

        assertFalse(testee.isExperimentEnabled())

        verify(mockLoadingBarExperimentDataStore).hasVariant
        verify(mockLoadingBarExperimentFeature.self()).isEnabled()
    }

    @Test
    fun whenHasNoVariantAndIsNotEnabledThenIsExperimentEnabledReturnsFalse() {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(false)

        assertFalse(testee.isExperimentEnabled())

        verify(mockLoadingBarExperimentDataStore).hasVariant
        verify(mockLoadingBarExperimentFeature.self()).isEnabled()
    }

    @Test
    fun whenUpdateCalledThenVariablesAreUpdated() = runTest {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(true)

        testee.update()

        assertFalse(testee.isExperimentEnabled())

        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(false)

        testee.update()

        assertFalse(testee.isExperimentEnabled())

        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(true)
        whenever(mockToggle.isEnabled()).thenReturn(false)

        testee.update()

        assertFalse(testee.isExperimentEnabled())

        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(true)
        whenever(mockToggle.isEnabled()).thenReturn(true)

        testee.update()

        assertTrue(testee.isExperimentEnabled())
    }

    @Test
    fun whenGetVariantThenVariantIsReturned() {
        whenever(mockLoadingBarExperimentDataStore.variant).thenReturn(true)

        assertTrue(testee.variant)

        verify(mockLoadingBarExperimentDataStore).variant
    }
}
