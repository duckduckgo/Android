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

package com.duckduckgo.app.attributed.metrics.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.attributed.metrics.api.AttributedMetric
import com.duckduckgo.app.attributed.metrics.api.EventStats
import com.duckduckgo.app.attributed.metrics.store.EventRepository
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealAttributedMetricClientTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val mockEventRepository: EventRepository = mock()
    private val mockPixel: Pixel = mock()
    private val mockMetricsState: AttributedMetricsState = mock()

    private lateinit var testee: RealAttributedMetricClient

    @Before
    fun setup() {
        testee = RealAttributedMetricClient(
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            eventRepository = mockEventRepository,
            pixel = mockPixel,
            metricsState = mockMetricsState,
        )
    }

    @Test
    fun whenCollectEventAndClientActiveEventIsCollected() = runTest {
        whenever(mockMetricsState.isActive()).thenReturn(true)

        testee.collectEvent("test_event")

        verify(mockEventRepository).collectEvent("test_event")
    }

    @Test
    fun whenCollectEventAndClientNotActiveEventIsNotCollected() = runTest {
        whenever(mockMetricsState.isActive()).thenReturn(false)

        testee.collectEvent("test_event")

        verify(mockEventRepository, never()).collectEvent(any())
    }

    @Test
    fun whenGetEventStatsAndClientActiveStatsAreReturned() = runTest {
        val expectedStats = EventStats(daysWithEvents = 5, rollingAverage = 2.5, totalEvents = 10)
        whenever(mockMetricsState.isActive()).thenReturn(true)
        whenever(mockEventRepository.getEventStats("test_event", 7)).thenReturn(expectedStats)

        val result = testee.getEventStats("test_event", 7)

        assertEquals(expectedStats, result)
        verify(mockEventRepository).getEventStats("test_event", 7)
    }

    @Test
    fun whenGetEventStatsAndClientNotActiveEmptyStatsAreReturned() = runTest {
        whenever(mockMetricsState.isActive()).thenReturn(false)

        val result = testee.getEventStats("test_event", 7)

        assertEquals(EventStats(daysWithEvents = 0, rollingAverage = 0.0, totalEvents = 0), result)
        verify(mockEventRepository, never()).getEventStats(any(), any())
    }

    @Test
    fun whenEmitMetricAndClientActiveMetricIsEmitted() = runTest {
        val testMetric = TestAttributedMetric()
        whenever(mockMetricsState.isActive()).thenReturn(true)

        testee.emitMetric(testMetric)

        verify(mockPixel).fire(pixelName = "test_pixel", parameters = mapOf("param" to "value"))
    }

    @Test
    fun whenEmitMetricAndClientNotActiveMetricIsNotEmitted() = runTest {
        val testMetric = TestAttributedMetric()
        whenever(mockMetricsState.isActive()).thenReturn(false)

        testee.emitMetric(testMetric)

        verifyNoInteractions(mockPixel)
    }

    private class TestAttributedMetric : AttributedMetric {
        override fun getPixelName(): String = "test_pixel"
        override suspend fun getMetricParameters(): Map<String, String> = mapOf("param" to "value")
    }
}
