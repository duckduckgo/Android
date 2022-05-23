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

import android.content.Context
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.bandwidth.impl.BandwidthPixelName.APPTP_BANDWIDTH
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = VpnServiceCallbacks::class
)
@SingleInstanceIn(AppScope::class)
class AppTpBandwidthCollector @Inject constructor(
    private val context: Context,
    private val bandwidthRepository: BandwidthRepository,
    private val pixel: Pixel,
    private val dispatcherProvider: DispatcherProvider
) : VpnServiceCallbacks {

    private var job = ConflatedJob()

    private suspend fun persistBucket(bucket: BandwidthData) = withContext(dispatcherProvider.io()) {
        Timber.v("AppTpBandwidthCollector - Persisting bucket $bucket")
        bandwidthRepository.persistBucket(bucket)
    }

    companion object {
        private const val BUCKET_PERIOD_MINUTES: Long = 5
        private const val REPORTING_PERIOD_MINUTES: Long = 60
        private const val NUM_BUCKETS_PER_PERIOD: Int = (REPORTING_PERIOD_MINUTES / BUCKET_PERIOD_MINUTES).toInt() + 1
        private const val KB = 1024
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        Timber.v("AppTpBandwidthCollector - onVpnStarted called")

        bandwidthRepository.deleteAllBuckets()

        job += coroutineScope.launch {
            while (isActive) {
                val currentBandwidthData = bandwidthRepository.getCurrentBandwidthData()
                persistBucket(BandwidthData(appBytes = currentBandwidthData.appBytes, totalBytes = currentBandwidthData.totalBytes))

                val buckets = bandwidthRepository.getBuckets()
                if (buckets.size == NUM_BUCKETS_PER_PERIOD) {
                    sendPixel(buckets)
                    bandwidthRepository.deleteAllBuckets()
                }
                delay(TimeUnit.MINUTES.toMillis(BUCKET_PERIOD_MINUTES))
            }
        }
    }

    private fun sendPixel(buckets: List<BandwidthData>) {
        val params = getPixelParams(buckets)
        pixel.fire(APPTP_BANDWIDTH, params)
    }

    private fun getPixelParams(
        buckets: List<BandwidthData>
    ): Map<String, String> {
        val period = buckets[NUM_BUCKETS_PER_PERIOD - 1].timestamp - buckets[0].timestamp
        val totalAppKiloBytes = (buckets[NUM_BUCKETS_PER_PERIOD - 1].appBytes - buckets[0].appBytes) / KB
        val totalDeviceKiloBytes = (buckets[NUM_BUCKETS_PER_PERIOD - 1].totalBytes - buckets[0].totalBytes) / KB

        val appKilobytes = arrayListOf<Long>()
        val deviceKilobytes = arrayListOf<Long>()

        for (i in 0..buckets.size - 2) {
            appKilobytes.add((buckets[i + 1].appBytes - buckets[i].appBytes) / KB)
            deviceKilobytes.add((buckets[i + 1].totalBytes - buckets[i].totalBytes) / KB)
        }

        return mapOf(
            AppTpBandwidthPixelParameter.PERIOD to period.toString(),
            AppTpBandwidthPixelParameter.TOTAL_APP_KILOBYTES to totalAppKiloBytes.toString(),
            AppTpBandwidthPixelParameter.TOTAL_DEVICE_KILOBYTES to totalDeviceKiloBytes.toString(),
            AppTpBandwidthPixelParameter.TIMESTAMPS to buckets.map { it.timestamp }.joinToString(","),
            AppTpBandwidthPixelParameter.APP_KILOBYTES to appKilobytes.joinToString(","),
            AppTpBandwidthPixelParameter.DEVICE_KILOBYTES to deviceKilobytes.joinToString(",")
        )
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        Timber.v("AppTpBandwidthCollector - onVpnStopped called")
        bandwidthRepository.deleteAllBuckets()
        job.cancel()
    }
}
