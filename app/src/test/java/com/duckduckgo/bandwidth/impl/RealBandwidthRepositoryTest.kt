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

import com.duckduckgo.bandwidth.store.BandwidthDao
import com.duckduckgo.bandwidth.store.BandwidthDatabase
import com.duckduckgo.bandwidth.store.BandwidthEntity
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealBandwidthRepositoryTest {

    private val mockTrafficStatsProvider: TrafficStatsProvider = mock()
    private val mockDatabase: BandwidthDatabase = mock()
    private val mockBandWidthDao: BandwidthDao = mock()

    private val testee = RealBandwidthRepository(mockTrafficStatsProvider, mockDatabase)

    @Before
    fun setup() {
        whenever(mockDatabase.bandwidthDao()).thenReturn(mockBandWidthDao)
    }

    @Test
    fun whenGetCurrentBandwidthDataThenReturnCurrentBandwidthDataTrafficStats() {
        whenever(mockTrafficStatsProvider.getAppRxBytes()).thenReturn(1)
        whenever(mockTrafficStatsProvider.getAppTxBytes()).thenReturn(2)

        whenever(mockTrafficStatsProvider.getTotalRxBytes()).thenReturn(4)
        whenever(mockTrafficStatsProvider.getTotalTxBytes()).thenReturn(5)

        val currentBandwidthData = testee.getCurrentBandwidthData()

        assertEquals(3, currentBandwidthData.appBytes)
        assertEquals(9, currentBandwidthData.totalBytes)
    }

    @Test
    fun whenGetStoredBandwidthDataThenReturnStoredBandwidthDataFromDatabase() {
        val bandwidthEntity = BandwidthEntity(timestamp = 123, appBytes = 456, totalBytes = 789)
        whenever(mockBandWidthDao.getBandwidth()).thenReturn(bandwidthEntity)

        val storedBandwidthData = testee.getStoredBandwidthData()

        val expectedBandwidthData = BandwidthData(
            timestamp = bandwidthEntity.timestamp,
            appBytes = bandwidthEntity.appBytes,
            totalBytes = bandwidthEntity.totalBytes
        )
        assertEquals(expectedBandwidthData, storedBandwidthData)
    }

    @Test
    fun whenPersistBandwidthDataThenPersistBandwidthDataToDatabase() {
        val bandwidthData = BandwidthData(appBytes = 123, totalBytes = 456)

        testee.persistBandwidthData(bandwidthData)

        val bandwidthEntity = BandwidthEntity(
            timestamp = bandwidthData.timestamp,
            appBytes = bandwidthData.appBytes,
            totalBytes = bandwidthData.totalBytes
        )

        verify(mockBandWidthDao).insert(bandwidthEntity)
        assertEquals(0, bandwidthEntity.id)
    }
}
