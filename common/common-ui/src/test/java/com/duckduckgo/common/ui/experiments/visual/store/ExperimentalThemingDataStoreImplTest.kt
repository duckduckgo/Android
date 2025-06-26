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
import com.duckduckgo.common.ui.experiments.visual.ExperimentalThemingFeature
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
class ExperimentalThemingDataStoreImplTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val experimentalThemingFeature = FakeFeatureToggleFactory.create(
        toggles = ExperimentalThemingFeature::class.java,
        store = FakeToggleStore(),
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        experimentalThemingFeature.self().setRawStoredState(State(enable = true))
        // experimentalThemingFeature.splitOmnibarFeature().setRawStoredState(State(enable = true))
        experimentalThemingFeature.singleOmnibarFeature().setRawStoredState(State(enable = true))
    }

//    @Test
//    fun `when split omnibar feature flag enabled, then experiment enabled`() = runTest {
//        val testee = createTestee()
//
//        Assert.assertTrue(testee.isSplitOmnibarEnabled.value)
//    }

//    @Test
//    fun `when split omnibar feature flag disabled, then experiment disabled`() = runTest {
////        experimentalThemingFeature.splitOmnibarFeature().setRawStoredState(State(enable = false))
//
//        val testee = createTestee()
//
//        Assert.assertFalse(testee.isSplitOmnibarEnabled.value)
//    }

    @Test
    fun `when single omnibar feature flag enabled and split disabled, then experiment enabled`() = runTest {
        // experimentalThemingFeature.splitOmnibarFeature().setRawStoredState(State(enable = false))
        val testee = createTestee()

        Assert.assertTrue(testee.isSingleOmnibarEnabled.value)
    }

    @Test
    fun `when single omnibar feature flag disabled, then experiment disabled`() = runTest {
        // experimentalThemingFeature.splitOmnibarFeature().setRawStoredState(State(enable = false))
        experimentalThemingFeature.singleOmnibarFeature().setRawStoredState(State(enable = false))

        val testee = createTestee()

        Assert.assertFalse(testee.isSingleOmnibarEnabled.value)
    }

    private fun createTestee(): ExperimentalThemingDataStoreImpl {
        return ExperimentalThemingDataStoreImpl(
            appCoroutineScope = coroutineRule.testScope,
            experimentalThemingFeature = experimentalThemingFeature,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }
}
