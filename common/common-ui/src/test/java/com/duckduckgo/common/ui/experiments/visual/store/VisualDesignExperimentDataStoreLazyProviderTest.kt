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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class VisualDesignExperimentDataStoreLazyProviderTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var experimentalUIThemingFeature: ExperimentalUIThemingFeature

    @Mock
    private lateinit var togglesInventory: FeatureTogglesInventory

    @Mock
    private lateinit var visualDesignExperimentDataStoreImplFactory: VisualDesignExperimentDataStoreImplFactory

    @Mock
    private lateinit var isExperimentEnabledFlowMock: StateFlow<Boolean>

    @Mock
    private lateinit var isDuckAIPoCEnabledFlowMock: StateFlow<Boolean>

    @Mock
    private lateinit var anyConflictingExperimentEnabledFlowMock: StateFlow<Boolean>

    @Mock
    private lateinit var visualDesignExperimentDataStoreImpl: VisualDesignExperimentDataStoreImpl

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(
            visualDesignExperimentDataStoreImplFactory.create(
                appCoroutineScope = coroutineRule.testScope,
                experimentalUIThemingFeature = experimentalUIThemingFeature,
                featureTogglesInventory = togglesInventory,
            ),
        ).thenReturn(visualDesignExperimentDataStoreImpl)
        whenever(visualDesignExperimentDataStoreImpl.isExperimentEnabled).thenReturn(isExperimentEnabledFlowMock)
        whenever(visualDesignExperimentDataStoreImpl.isDuckAIPoCEnabled).thenReturn(isDuckAIPoCEnabledFlowMock)
        whenever(visualDesignExperimentDataStoreImpl.anyConflictingExperimentEnabled).thenReturn(anyConflictingExperimentEnabledFlowMock)
    }

    @Test(expected = AssertionError::class)
    fun `provider throws an AssertionError if store accessed before initializing`() = runTest {
        val testee = createTestee()
        testee.isExperimentEnabled
    }

    @Test
    fun `initializing creates a valid store instance`() = runTest {
        val testee = createTestee()
        testee.initialize()

        Assert.assertEquals(isExperimentEnabledFlowMock, testee.isExperimentEnabled)
        Assert.assertEquals(isDuckAIPoCEnabledFlowMock, testee.isDuckAIPoCEnabled)
        Assert.assertEquals(anyConflictingExperimentEnabledFlowMock, testee.anyConflictingExperimentEnabled)

        testee.onPrivacyConfigDownloaded()
        verify(visualDesignExperimentDataStoreImpl).onPrivacyConfigDownloaded()

        testee.changeExperimentFlagPreference(true)
        verify(visualDesignExperimentDataStoreImpl).changeExperimentFlagPreference(true)

        testee.changeDuckAIPoCFlagPreference(true)
        verify(visualDesignExperimentDataStoreImpl).changeDuckAIPoCFlagPreference(true)
    }

    @Test
    fun `initializing is idempotent`() = runTest {
        val testee = createTestee()

        val jobs = listOf(
            async { testee.initialize() },
            async { testee.initialize() },
            async { testee.initialize() },
        )

        val results = jobs.awaitAll()
        results.forEach {
            Assert.assertEquals(visualDesignExperimentDataStoreImpl, it)
        }

        verify(visualDesignExperimentDataStoreImplFactory, times(1)).create(
            appCoroutineScope = coroutineRule.testScope,
            experimentalUIThemingFeature = experimentalUIThemingFeature,
            featureTogglesInventory = togglesInventory,
        )
    }

    private fun createTestee(): VisualDesignExperimentDataStoreLazyProvider {
        return VisualDesignExperimentDataStoreLazyProvider(
            appCoroutineScope = coroutineRule.testScope,
            experimentalUIThemingFeature = experimentalUIThemingFeature,
            featureTogglesInventory = togglesInventory,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            visualDesignExperimentDataStoreImplFactory = visualDesignExperimentDataStoreImplFactory,
        )
    }
}
