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
import com.duckduckgo.app.widget.experiment.store.WidgetSearchCountDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PostCtaExperienceExperimentTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: PostCtaExperienceExperimentImpl

    private val mockDispatcherProvider = coroutineRule.testDispatcherProvider
    private val mockPostCtaExperienceToggles: PostCtaExperienceToggles = mock()
    private val mockPostCtaExperiencePixelsPlugin: PostCtaExperiencePixelsPlugin = mock()
    private val mockPixel: Pixel = mock()
    private val mockWidgetSearchCountDataStore: WidgetSearchCountDataStore = mock()

    @Before
    fun setup() {
        testee = PostCtaExperienceExperimentImpl(
            dispatcherProvider = mockDispatcherProvider,
            postCtaExperienceToggles = mockPostCtaExperienceToggles,
            postCtaExperiencePixelsPlugin = mockPostCtaExperiencePixelsPlugin,
            pixel = mockPixel,
            widgetSearchCountDataStore = mockWidgetSearchCountDataStore,
        )
    }

    @Test
    fun whenFireWidgetSearchXCountAndCountIs1ThenIncrementAndGetCountAndNoPixelFired() = runTest {
        whenever(mockWidgetSearchCountDataStore.getWidgetSearchCount()).thenReturn(1)

        testee.fireWidgetSearchXCount()

        verify(mockWidgetSearchCountDataStore).incrementWidgetSearchCount()
        verify(mockWidgetSearchCountDataStore).getWidgetSearchCount()
        verify(mockPostCtaExperiencePixelsPlugin, never()).getWidgetSearch3xMetric()
        verify(mockPostCtaExperiencePixelsPlugin, never()).getWidgetSearch5xMetric()
    }

    @Test
    fun whenFireWidgetSearchXCountAndCountIs3ThenIncrementAndGetCountAnd3xPixelFired() = runTest {
        whenever(mockWidgetSearchCountDataStore.getWidgetSearchCount()).thenReturn(3)

        testee.fireWidgetSearchXCount()

        verify(mockWidgetSearchCountDataStore).incrementWidgetSearchCount()
        verify(mockWidgetSearchCountDataStore).getWidgetSearchCount()
        verify(mockPostCtaExperiencePixelsPlugin).getWidgetSearch3xMetric()
        verify(mockPostCtaExperiencePixelsPlugin, never()).getWidgetSearch5xMetric()
    }

    @Test
    fun whenFireWidgetSearchXCountAndCountIs5ThenIncrementAndGetCountAnd5xPixelFired() = runTest {
        whenever(mockWidgetSearchCountDataStore.getWidgetSearchCount()).thenReturn(5)

        testee.fireWidgetSearchXCount()

        verify(mockWidgetSearchCountDataStore).incrementWidgetSearchCount()
        verify(mockWidgetSearchCountDataStore).getWidgetSearchCount()
        verify(mockPostCtaExperiencePixelsPlugin, never()).getWidgetSearch3xMetric()
        verify(mockPostCtaExperiencePixelsPlugin).getWidgetSearch5xMetric()
    }

    @Test
    fun whenFireWidgetSearchXCountAndCountIs4ThenIncrementAndGetCountAndNoPixelFired() = runTest {
        whenever(mockWidgetSearchCountDataStore.getWidgetSearchCount()).thenReturn(4)

        testee.fireWidgetSearchXCount()

        verify(mockWidgetSearchCountDataStore).incrementWidgetSearchCount()
        verify(mockWidgetSearchCountDataStore).getWidgetSearchCount()
        verify(mockPostCtaExperiencePixelsPlugin, never()).getWidgetSearch3xMetric()
        verify(mockPostCtaExperiencePixelsPlugin, never()).getWidgetSearch5xMetric()
    }

    @Test
    fun whenFireWidgetSearchXCountAndCountIs6ThenIncrementAndGetCountAndNoPixelFired() = runTest {
        whenever(mockWidgetSearchCountDataStore.getWidgetSearchCount()).thenReturn(6)

        testee.fireWidgetSearchXCount()

        verify(mockWidgetSearchCountDataStore).incrementWidgetSearchCount()
        verify(mockWidgetSearchCountDataStore).getWidgetSearchCount()
        verify(mockPostCtaExperiencePixelsPlugin, never()).getWidgetSearch3xMetric()
        verify(mockPostCtaExperiencePixelsPlugin, never()).getWidgetSearch5xMetric()
    }
}
