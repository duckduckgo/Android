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

package com.duckduckgo.common.ui.experiments.visual.store

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.experiments.visual.ExperimentalUIThemingFeature
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class VisualDesignExperimentDataStoreImplTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var experimentalUIThemingFeature: ExperimentalUIThemingFeature

    @Mock
    private lateinit var experimentalUIThemingFeatureToggle: Toggle

    @Mock
    private lateinit var visualDesignFeatureToggle: Toggle

    @Mock
    private lateinit var duckChatPoCToggle: Toggle

    @Mock
    private lateinit var togglesInventory: FeatureTogglesInventory

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(experimentalUIThemingFeature.self()).thenReturn(experimentalUIThemingFeatureToggle)
        whenever(experimentalUIThemingFeature.visualUpdatesFeature()).thenReturn(visualDesignFeatureToggle)
        whenever(experimentalUIThemingFeature.duckAIPoCFeature()).thenReturn(duckChatPoCToggle)
    }

    private fun whenVisualExperimentEnabled(enabled: Boolean) {
        whenever(experimentalUIThemingFeatureToggle.isEnabled()).thenReturn(enabled)
        whenever(visualDesignFeatureToggle.isEnabled()).thenReturn(enabled)
        whenever(duckChatPoCToggle.isEnabled()).thenReturn(enabled)
    }

    @Test
    fun `when Duck AI PoC FF enabled and experiment enabled, Duck AI PoC enabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(emptyList())
        whenever(experimentalUIThemingFeatureToggle.isEnabled()).thenReturn(true)
        whenever(visualDesignFeatureToggle.isEnabled()).thenReturn(true)
        whenever(duckChatPoCToggle.isEnabled()).thenReturn(true)

        val testee = createTestee()

        Assert.assertTrue(testee.isDuckAIPoCEnabled.value)
    }

    @Test
    fun `when Duck AI PoC FF enabled and experiment disabled, Duck AI PoC disabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(emptyList())
        whenever(experimentalUIThemingFeatureToggle.isEnabled()).thenReturn(false)
        whenever(visualDesignFeatureToggle.isEnabled()).thenReturn(false)
        whenever(duckChatPoCToggle.isEnabled()).thenReturn(true)

        val testee = createTestee()

        Assert.assertFalse(testee.isDuckAIPoCEnabled.value)
    }

    @Test
    fun `when Duck AI PoC FF disabled but experiment enabled, Duck AI PoC disabled`() = runTest {
        whenever(togglesInventory.getAllActiveExperimentToggles()).thenReturn(emptyList())
        whenever(experimentalUIThemingFeatureToggle.isEnabled()).thenReturn(true)
        whenever(visualDesignFeatureToggle.isEnabled()).thenReturn(true)
        whenever(duckChatPoCToggle.isEnabled()).thenReturn(false)

        val testee = createTestee()

        Assert.assertFalse(testee.isDuckAIPoCEnabled.value)
    }

    private fun createTestee(): VisualDesignExperimentDataStoreImpl {
        return VisualDesignExperimentDataStoreImpl(
            appCoroutineScope = coroutineRule.testScope,
            experimentalUIThemingFeature = experimentalUIThemingFeature,
        )
    }
}
