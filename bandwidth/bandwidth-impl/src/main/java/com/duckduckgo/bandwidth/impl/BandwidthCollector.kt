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
import com.duckduckgo.bandwidth.impl.BandwidthPixelParameter.APP_BYTES
import com.duckduckgo.bandwidth.impl.BandwidthPixelParameter.PERIOD
import com.duckduckgo.bandwidth.impl.BandwidthPixelParameter.TOTAL_BYTES
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface BandwidthCollector {
    fun collect()
}

@ContributesBinding(AppScope::class)
class RealBandwidthCollector @Inject constructor(
    val bandwidthRepository: BandwidthRepository,
    val pixel: Pixel
) : BandwidthCollector {
    override fun collect() {

        val lastBandwidthData = bandwidthRepository.getStoredBandwidthData()
        val currentBandwidthData = bandwidthRepository.getCurrentBandwidthData().also { bandwidthRepository.persistBandwidthData(it) }

        if (lastBandwidthData == null) return
        if (lastBandwidthData.totalBytes > currentBandwidthData.totalBytes) return

        val period = currentBandwidthData.timestamp - lastBandwidthData.timestamp
        val appBytes = currentBandwidthData.appBytes - lastBandwidthData.appBytes
        val totalBytes = currentBandwidthData.totalBytes - lastBandwidthData.totalBytes

        val params = mapOf(
            PERIOD to period.toString(),
            APP_BYTES to appBytes.toString(),
            TOTAL_BYTES to totalBytes.toString()
        )

        pixel.fire(BANDWIDTH, params)
    }
}
