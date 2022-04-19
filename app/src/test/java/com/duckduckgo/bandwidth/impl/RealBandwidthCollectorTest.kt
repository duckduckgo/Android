/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.bandwidth.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.bandwidth.impl.BandwidthPixelName.BANDWIDTH
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealBandwidthCollectorTest {

    private val mockBandwidthRepository: BandwidthRepository = mock()
    private val mockPixel: Pixel = mock()

    private val testee = RealBandwidthCollector(mockBandwidthRepository, mockPixel)

    @Test
    fun whenCollectBandwidthAndStoredBandwidthIsNullThenPersistCurrentBandwidthAndReturn() {
        val bandwidthData = BandwidthData(timestamp = 123, appBytes = 456, totalBytes = 789)

        whenever(mockBandwidthRepository.getStoredBandwidthData()).thenReturn(null)
        whenever(mockBandwidthRepository.getCurrentBandwidthData()).thenReturn(bandwidthData)

        testee.collect()

        verify(mockBandwidthRepository).persistBandwidthData(bandwidthData)
        verifyNoInteractions(mockPixel)
    }

    @Test
    fun whenCollectBandwidthAndStoredTotalBytesIsLargerThanCurrentTotalBytesThenPersistCurrentBandwidthAndReturn() {
        val bandwidthData = BandwidthData(timestamp = 123, appBytes = 456, totalBytes = 789)

        whenever(mockBandwidthRepository.getStoredBandwidthData()).thenReturn(BandwidthData(timestamp = 123, appBytes = 456, totalBytes = 999))
        whenever(mockBandwidthRepository.getCurrentBandwidthData()).thenReturn(bandwidthData)

        testee.collect()

        verify(mockBandwidthRepository).persistBandwidthData(bandwidthData)
        verifyNoInteractions(mockPixel)
    }

    @Test
    fun whenCollectBandwidthThenPersistCurrentBandwidthAndSendPixel() {
        val currentBandwidthData = BandwidthData(timestamp = 123, appBytes = 456, totalBytes = 789)
        val lastBandwidthData = BandwidthData(timestamp = 100, appBytes = 456, totalBytes = 689)

        whenever(mockBandwidthRepository.getStoredBandwidthData()).thenReturn(lastBandwidthData)
        whenever(mockBandwidthRepository.getCurrentBandwidthData()).thenReturn(currentBandwidthData)

        testee.collect()

        verify(mockBandwidthRepository).persistBandwidthData(currentBandwidthData)

        val period = currentBandwidthData.timestamp - lastBandwidthData.timestamp
        val appBytes = currentBandwidthData.appBytes - lastBandwidthData.appBytes
        val totalBytes = currentBandwidthData.totalBytes - lastBandwidthData.totalBytes

        val params = mapOf(
            BandwidthPixelParameter.PERIOD to period.toString(),
            BandwidthPixelParameter.APP_BYTES to appBytes.toString(),
            BandwidthPixelParameter.TOTAL_BYTES to totalBytes.toString()
        )

        verify(mockPixel).fire(BANDWIDTH, params)
        assertEquals("m_bandwidth", BANDWIDTH.pixelName)
    }
}
