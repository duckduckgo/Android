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
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlin.jvm.java
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockitoAnnotations

@Suppress("DenyListedApi")
class VisualDesignExperimentDataStoreImplTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val experimentalUIThemingFeature = FakeFeatureToggleFactory.create(
        toggles = ExperimentalUIThemingFeature::class.java,
        store = FakeToggleStore(),
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        experimentalUIThemingFeature.self().setRawStoredState(State(enable = true))
        experimentalUIThemingFeature.visualUpdatesFeature().setRawStoredState(State(enable = true))
        experimentalUIThemingFeature.visualUpdatesWithoutBottomBarFeature().setRawStoredState(State(enable = true))
    }

    @Test
    fun `when split omnibar feature flag enabled, then experiment enabled`() = runTest {
        experimentalUIThemingFeature.visualUpdatesFeature().setRawStoredState(State(enable = false))

        val testee = createTestee()

        Assert.assertTrue(testee.isNewDesignWithoutBottomBarEnabled.value)
    }

    @Test
    fun `when split omnibar feature flag disabled, then experiment disabled`() = runTest {
        experimentalUIThemingFeature.visualUpdatesFeature().setRawStoredState(State(enable = false))
        experimentalUIThemingFeature.visualUpdatesWithoutBottomBarFeature().setRawStoredState(State(enable = false))

        val testee = createTestee()

        Assert.assertFalse(testee.isNewDesignWithoutBottomBarEnabled.value)
    }

    @Test
    fun `when new design feature flag enabled, then experiment enabled`() = runTest {
        val testee = createTestee()

        Assert.assertTrue(testee.isNewDesignEnabled.value)
    }

    @Test
    fun `when new design feature flag disabled, then experiment disabled`() = runTest {
        experimentalUIThemingFeature.visualUpdatesFeature().setRawStoredState(State(enable = false))

        val testee = createTestee()

        Assert.assertFalse(testee.isNewDesignEnabled.value)
    }

    private fun createTestee(): VisualDesignExperimentDataStoreImpl {
        return VisualDesignExperimentDataStoreImpl(
            appCoroutineScope = coroutineRule.testScope,
            experimentalUIThemingFeature = experimentalUIThemingFeature,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }
}
