/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.job

import android.content.Context
import androidx.work.*
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.VPN_TESTERS_DAILY_REPORT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.VPN_DATA_RECEIVED
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.VPN_DATA_SENT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.VPN_TIME_RUNNING
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.VPN_TRACKERS_BLOCKED
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.VPN_UUID
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class VpnStatsReportingRequestBuilder @Inject constructor() {

    fun buildWorkRequest(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<VpnStatsReportingWorker>(24, TimeUnit.HOURS)
            .addTag(VPN_STATS_REPORTING_WORK_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_INTERVAL, BACKOFF_TIME_UNIT)
            .setConstraints(networkAvailable())
            .build()
    }

    private fun networkAvailable() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    companion object {
        const val VPN_STATS_REPORTING_WORK_TAG = "VpnStatsReportingTag"
        private const val BACKOFF_INTERVAL = 10L
        private val BACKOFF_TIME_UNIT = TimeUnit.MINUTES
    }
}

class VpnStatsReportingWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var vpnDatabase: VpnDatabase

    @Inject
    lateinit var pixel: Pixel

    override suspend fun doWork(): Result {
        Timber.i("VpnStatsReportingWorker running")
        val current = vpnDatabase.vpnStatsDao().getCurrent()
        return if (current != null) {
            val params = mapOf(
                VPN_TIME_RUNNING to current.timeRunning.toString(),
                VPN_DATA_RECEIVED to current.dataReceived.toString(),
                VPN_DATA_SENT to current.dataSent.toString(),
                VPN_UUID to (vpnDatabase.vpnStateDao().getOneOff()?.uuid ?: "unknown"),
                VPN_TRACKERS_BLOCKED to vpnDatabase.vpnTrackerDao().getTrackersByCompanyAfterSync(current.startedAt).size.toString()
            )
            pixel.fire(VPN_TESTERS_DAILY_REPORT, params)
            Timber.i("Sending daily pixel report $params")
            Result.success()
        } else {
            Timber.i("Daily pixel could not be sent, waiting for next schedule")
            Result.success()
        }
    }

}
