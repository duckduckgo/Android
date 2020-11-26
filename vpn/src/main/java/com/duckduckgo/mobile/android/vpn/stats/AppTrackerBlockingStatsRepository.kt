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

import com.duckduckgo.mobile.android.vpn.model.VpnDataStats
import com.duckduckgo.mobile.android.vpn.model.VpnState
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.threeten.bp.LocalDateTime
import javax.inject.Inject

class AppTrackerBlockingStatsRepository @Inject constructor(vpnDatabase: VpnDatabase) {

    private val trackerDao = vpnDatabase.vpnTrackerDao()
    private val statsDao = vpnDatabase.vpnDataStatsDao()
    private val stateDao = vpnDatabase.vpnStateDao()
    private val runningTimeDao = vpnDatabase.vpnRunningStatsDao()

    fun getVpnState(): Flow<VpnState> {
        return stateDao.get().distinctUntilChanged()
    }

    fun getVpnTrackers(startTime: () -> String, endTime: String = noEndDate()): Flow<List<VpnTrackerAndCompany>> {
        return trackerDao.getTrackersBetween(startTime(), endTime)
            .distinctUntilChanged()
            .map { it.filter { tracker -> tracker.tracker.timestamp >= startTime() } }
    }

    fun getRunningTimeMillis(startTime: () -> String, endTime: String = noEndDate()): Flow<Long> {
        return runningTimeDao.getRunningStatsBetween(startTime(), endTime)
            .distinctUntilChanged()
            .map { list -> list.filter { it.id >= startTime() } }
            .transform { runningTimes ->
                emit(runningTimes.sumOf { it.timeRunningMillis })
            }
            .flowOn(Dispatchers.Default)
    }

    fun getVpnDataStats(startTime: () -> String, endTime: String = noEndDate()): Flow<DataStats> {
        return statsDao.getDataStatsBetween(startTime(), endTime)
            .distinctUntilChanged()
            .map { list -> list.filter { it.id >= startTime() } }
            .transform {
                emit(calculateDataTotals(it))
            }.flowOn(Dispatchers.Default)
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

    private fun noEndDate(): String {
        return DatabaseDateFormatter.timestamp(LocalDateTime.of(9999, 1, 1, 0, 0))
    }

    data class DataStats(val sent: DataTransfer = DataTransfer(), val received: DataTransfer = DataTransfer())
    data class DataTransfer(val dataSize: Long = 0, val numberPackets: Long = 0)
}
