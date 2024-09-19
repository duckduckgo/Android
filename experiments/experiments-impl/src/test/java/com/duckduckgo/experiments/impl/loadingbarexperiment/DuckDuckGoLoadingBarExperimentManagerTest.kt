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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.experiments.impl.loadingbarexperiment.DuckDuckGoLoadingBarExperimentManager
import com.duckduckgo.experiments.impl.loadingbarexperiment.LoadingBarExperimentDataStore
import com.duckduckgo.experiments.impl.loadingbarexperiment.LoadingBarExperimentFeature
import com.duckduckgo.feature.toggles.api.Toggle
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

class DuckDuckGoLoadingBarExperimentManagerTest {

    private lateinit var testee: DuckDuckGoLoadingBarExperimentManager

    private val mockLoadingBarExperimentDataStore: LoadingBarExperimentDataStore = mock()
    private val mockLoadingBarExperimentFeature: LoadingBarExperimentFeature = mock()
    private val mockToggle: Toggle = mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun setup() {
        whenever(mockLoadingBarExperimentFeature.self()).thenReturn(mockToggle)
    }

    @Test
    fun whenHasVariantAndIsEnabledThenIsExperimentEnabledReturnsTrue() {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(true)
        whenever(mockToggle.isEnabled()).thenReturn(true)

        initialize()

        assertTrue(testee.isExperimentEnabled())
        verify(mockLoadingBarExperimentDataStore).hasVariant
        verify(mockLoadingBarExperimentFeature.self()).isEnabled()
    }

    @Test
    fun whenHasNoVariantThenIsExperimentEnabledReturnsFalse() {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(true)

        initialize()

        assertFalse(testee.isExperimentEnabled())
        verify(mockLoadingBarExperimentDataStore).hasVariant
    }

    @Test
    fun whenIsNotEnabledThenIsExperimentEnabledReturnsFalse() {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(true)
        whenever(mockToggle.isEnabled()).thenReturn(false)

        initialize()

        assertFalse(testee.isExperimentEnabled())
        verify(mockLoadingBarExperimentDataStore).hasVariant
    }

    @Test
    fun whenHasNoVariantAndIsNotEnabledThenIsExperimentEnabledReturnsFalse() {
        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(false)

        initialize()

        assertFalse(testee.isExperimentEnabled())
        verify(mockLoadingBarExperimentDataStore).hasVariant
    }

    @Test
    fun whenUpdateCalledThenCachedVariablesAreUpdated() = runTest {
        initialize()

        verify(mockLoadingBarExperimentDataStore, times(1)).hasVariant
        verify(mockToggle, times(1)).isEnabled()

        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(true)

        testee.update()

        assertFalse(testee.isExperimentEnabled())
        verify(mockLoadingBarExperimentDataStore, times(2)).hasVariant
        verify(mockToggle, times(2)).isEnabled()

        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(false)
        whenever(mockToggle.isEnabled()).thenReturn(false)

        testee.update()

        assertFalse(testee.isExperimentEnabled())
        verify(mockLoadingBarExperimentDataStore, times(3)).hasVariant
        verify(mockToggle, times(3)).isEnabled()

        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(true)
        whenever(mockToggle.isEnabled()).thenReturn(false)

        testee.update()

        assertFalse(testee.isExperimentEnabled())
        verify(mockLoadingBarExperimentDataStore, times(4)).hasVariant
        verify(mockToggle, times(4)).isEnabled()

        whenever(mockLoadingBarExperimentDataStore.hasVariant).thenReturn(true)
        whenever(mockToggle.isEnabled()).thenReturn(true)

        testee.update()

        assertTrue(testee.isExperimentEnabled())
        verify(mockLoadingBarExperimentDataStore, times(5)).hasVariant
        verify(mockToggle, times(5)).isEnabled()
    }

    @Test
    fun whenGetVariantThenVariantIsReturned() {
        whenever(mockLoadingBarExperimentDataStore.variant).thenReturn(true)

        initialize()

        assertTrue(testee.variant)
        verify(mockLoadingBarExperimentDataStore).variant
    }

    private fun initialize() {
        testee = DuckDuckGoLoadingBarExperimentManager(
            mockLoadingBarExperimentDataStore,
            mockLoadingBarExperimentFeature,
            TestScope(),
            coroutineTestRule.testDispatcherProvider,
            isMainProcess = true,
        )
    }
}
