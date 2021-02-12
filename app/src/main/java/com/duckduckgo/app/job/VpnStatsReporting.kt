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
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.VPN_TESTERS_DAILY_REPORT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.VPN_DATA_RECEIVED
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.VPN_DATA_SENT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.VPN_TIME_RUNNING
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.VPN_TRACKERS_BLOCKED
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.VPN_TRACKER_COMPANIES_BLOCKED
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.VPN_UUID
import com.duckduckgo.mobile.android.vpn.model.dateOfPreviousMidnight
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.DataStats
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import kotlinx.coroutines.flow.firstOrNull
import org.threeten.bp.LocalDateTime
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

class VpnStatsReportingWorkerInjectorPlugin(
    private val vpnDatabase: VpnDatabase,
    private val appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository,
    private val pixel: Pixel
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is VpnStatsReportingWorker) {
            worker.vpnDatabase = vpnDatabase
            worker.vpnRepository = appTrackerBlockingStatsRepository
            worker.pixel = pixel
            return true
        }
        return false
    }
}

class VpnStatsReportingWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var vpnDatabase: VpnDatabase

    @Inject
    lateinit var vpnRepository: AppTrackerBlockingStatsRepository

    @Inject
    lateinit var pixel: Pixel

    override suspend fun doWork(): Result {
        val queryStartTime = DatabaseDateFormatter.timestamp(LocalDateTime.now().minusDays(1).toLocalDate().atStartOfDay())
        val queryEndTime = dateOfPreviousMidnight()
        Timber.i("VpnStatsReportingWorker running. Finding stats between $queryStartTime and $queryEndTime")

        val uuid = vpnRepository.getVpnState().firstOrNull()
        if (uuid == null) {
            Timber.e("UUID has not been populated")
            return Result.failure()
        }

        val runningTimeMillis = vpnRepository.getRunningTimeMillis({ queryStartTime }, queryEndTime).firstOrNull() ?: 0L
        val trackers = vpnRepository.getVpnTrackers({ queryStartTime }, queryEndTime).firstOrNull() ?: emptyList()
        val trackersByCompany = trackers.groupBy { it.trackerCompany.trackerCompanyId }
        val dataTransferredStats = vpnRepository.getVpnDataStats({ queryStartTime }, queryEndTime).firstOrNull() ?: DataStats()

        val params = mapOf(
            VPN_UUID to uuid.uuid,
            VPN_TIME_RUNNING to TimeUnit.MILLISECONDS.toSeconds(runningTimeMillis).toString(),
            VPN_DATA_RECEIVED to dataTransferredStats.received.dataSize.toString(),
            VPN_DATA_SENT to dataTransferredStats.sent.dataSize.toString(),
            VPN_TRACKERS_BLOCKED to trackers.size.toString(),
            VPN_TRACKER_COMPANIES_BLOCKED to trackersByCompany.size.toString()
        )
        pixel.fire(VPN_TESTERS_DAILY_REPORT, params)
        Timber.w("Sending daily pixel report $params")
        return Result.success()
    }

}
