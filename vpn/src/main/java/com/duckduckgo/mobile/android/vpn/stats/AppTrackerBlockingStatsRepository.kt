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

package com.duckduckgo.mobile.android.vpn.stats

import androidx.annotation.WorkerThread
import com.duckduckgo.mobile.android.vpn.dao.VpnPhoenixEntity
import com.duckduckgo.mobile.android.vpn.model.*
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.threeten.bp.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AppTrackerBlockingStatsRepository @Inject constructor(vpnDatabase: VpnDatabase) {

    private val trackerDao = vpnDatabase.vpnTrackerDao()
    private val statsDao = vpnDatabase.vpnDataStatsDao()
    private val stateDao = vpnDatabase.vpnStateDao()
    private val runningTimeDao = vpnDatabase.vpnRunningStatsDao()
    private val phoenixDao = vpnDatabase.vpnPhoenixDao()

    fun getVpnState(): Flow<VpnState> {
        return stateDao.get().distinctUntilChanged()
    }

    fun getVpnTrackers(
        startTime: () -> String,
        endTime: String = noEndDate()
    ): Flow<List<VpnTracker>> {
        return trackerDao.getTrackersBetween(startTime(), endTime)
            .conflate()
            .distinctUntilChanged()
            .map { it.filter { tracker -> tracker.timestamp >= startTime() } }
            .flowOn(Dispatchers.Default)
    }

    @WorkerThread
    fun getVpnTrackersSync(
        startTime: () -> String,
        endTime: String = noEndDate()
    ): List<VpnTracker> {
        return trackerDao.getTrackersBetweenSync(startTime(), endTime)
            .filter { tracker -> tracker.timestamp >= startTime() }
    }

    @WorkerThread
    fun getMostRecentVpnTrackers(startTime: () -> String): Flow<List<BucketizedVpnTracker>> {
        return trackerDao.getPagedTrackersSince(startTime())
            .conflate()
            .distinctUntilChanged()
    }

    @WorkerThread
    fun getTrackersForAppFromDate(date: String, packageName: String): Flow<List<VpnTrackerCompanySignal>> {
        return trackerDao.getTrackersForAppFromDate(date, packageName)
            .conflate()
            .distinctUntilChanged()
    }

    fun getRunningTimeMillis(
        startTime: () -> String,
        endTime: String = noEndDate()
    ): Flow<Long> {
        return runningTimeDao.getRunningStatsBetween(startTime(), endTime)
            .conflate()
            .distinctUntilChanged()
            .map { list -> list.filter { it.id >= startTime() } }
            .transform { runningTimes ->
                emit(runningTimes.sumOf { it.timeRunningMillis })
            }
            .flowOn(Dispatchers.Default)
    }

    fun getVpnDataStats(
        startTime: () -> String,
        endTime: String = noEndDate()
    ): Flow<DataStats> {
        return statsDao.getDataStatsBetween(startTime(), endTime)
            .conflate()
            .distinctUntilChanged()
            .map { list -> list.filter { it.id >= startTime() } }
            .transform {
                emit(calculateDataTotals(it))
            }.flowOn(Dispatchers.Default)
    }

    @WorkerThread
    fun getVpnRestartHistory(): List<VpnPhoenixEntity> {
        return phoenixDao.restarts()
            .filter { it.timestamp >= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1) }
    }

    @WorkerThread
    fun deleteVpnRestartHistory() {
        phoenixDao.delete()
    }

    @WorkerThread
    fun getBlockedTrackersCountBetween(
        startTime: () -> String,
        endTime: String = noEndDate()
    ): Flow<Int> {
        return trackerDao.getTrackersCountBetween(startTime(), endTime)
            .conflate()
            .distinctUntilChanged()
    }

    @WorkerThread
    fun getTrackingAppsCountBetween(
        startTime: () -> String,
        endTime: String = noEndDate()
    ): Flow<Int> {
        return trackerDao.getTrackingAppsCountBetween(startTime(), endTime)
            .conflate()
            .distinctUntilChanged()
    }

    private fun calculateDataTotals(dataStats: List<VpnDataStats>): DataStats {

        var dataSent = 0L
        var packetsSent = 0L
        var dataReceived = 0L
        var packetsReceived = 0L

        dataStats.forEach {
            dataReceived += it.dataReceived
            dataSent += it.dataSent
            packetsReceived += it.packetsReceived
            packetsSent += it.packetsSent
        }

        return DataStats(
            sent = DataTransfer(dataSent, packetsSent),
            received = DataTransfer(dataReceived, packetsReceived)
        )
    }

    fun noStartDate(): String {
        return DatabaseDateFormatter.timestamp(LocalDateTime.of(2000, 1, 1, 0, 0))
    }

    private fun noEndDate(): String {
        return DatabaseDateFormatter.timestamp(LocalDateTime.of(9999, 1, 1, 0, 0))
    }

    data class DataStats(
        val sent: DataTransfer = DataTransfer(),
        val received: DataTransfer = DataTransfer()
    )

    data class DataTransfer(
        val dataSize: Long = 0,
        val numberPackets: Long = 0
    )
}
