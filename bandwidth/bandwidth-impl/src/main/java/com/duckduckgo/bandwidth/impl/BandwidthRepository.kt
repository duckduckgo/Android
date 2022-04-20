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

import com.duckduckgo.bandwidth.store.BandwidthDatabase
import com.duckduckgo.bandwidth.store.BandwidthEntity
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface BandwidthRepository {

    fun getCurrentBandwidthData(): BandwidthData
    fun persistBandwidthData(bandwidthData: BandwidthData)
    fun getStoredBandwidthData(): BandwidthData?
}

@ContributesBinding(AppScope::class)
class RealBandwidthRepository @Inject constructor(
    val trafficStatsProvider: TrafficStatsProvider,
    val database: BandwidthDatabase
) : BandwidthRepository {

    override fun getCurrentBandwidthData(): BandwidthData {
        return BandwidthData(
            appBytes = trafficStatsProvider.getAppRxBytes() + trafficStatsProvider.getAppTxBytes(),
            totalBytes = trafficStatsProvider.getTotalRxBytes() + trafficStatsProvider.getTotalTxBytes()
        )
    }

    override fun persistBandwidthData(bandwidthData: BandwidthData) {
        database.bandwidthDao().insert(
            BandwidthEntity(
                timestamp = bandwidthData.timestamp,
                appBytes = bandwidthData.appBytes,
                totalBytes = bandwidthData.totalBytes
            )
        )
    }

    override fun getStoredBandwidthData(): BandwidthData? {
        val bandwidthEntity = database.bandwidthDao().getBandwidth() ?: return null

        return BandwidthData(
            timestamp = bandwidthEntity.timestamp,
            appBytes = bandwidthEntity.appBytes,
            totalBytes = bandwidthEntity.totalBytes
        )
    }
}
