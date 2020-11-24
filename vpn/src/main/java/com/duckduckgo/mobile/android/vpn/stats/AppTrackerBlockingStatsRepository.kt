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

import androidx.lifecycle.LiveData
import com.duckduckgo.mobile.android.vpn.model.VpnDataStats
import com.duckduckgo.mobile.android.vpn.model.VpnRunningStats
import com.duckduckgo.mobile.android.vpn.model.VpnState
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class AppTrackerBlockingStatsRepository @Inject constructor(private val vpnDatabase: VpnDatabase) {

    fun getVpnState(): VpnState {
        return vpnDatabase.vpnStateDao().getOneOff() ?: VpnState(uuid = "unknown")
    }

    fun getVpnStateAsync(): LiveData<VpnState> {
        return vpnDatabase.vpnStateDao().get()
    }

    suspend fun getRunningTimeMillis(startTime: String): Long {
        return getVpnRunningStats(startTime).firstOrNull()
            ?.sumOf { it.timeRunningMillis }
            ?: 0L
    }

    private fun getVpnRunningStats(startTime: String): Flow<List<VpnRunningStats>> {
        return vpnDatabase.vpnRunningStatsDao().get(startTime)
    }

    private fun getVpnDataStats(startTime: String): Flow<List<VpnDataStats>> {
        return vpnDatabase.vpnDataStatsDao().get(startTime)
    }

    fun getVpnTrackers(startTime: String): Flow<List<VpnTrackerAndCompany>> {
        return vpnDatabase.vpnTrackerDao().getTrackersAfterSync(startTime)
    }

    suspend fun getDataStats(midnight: String): DataStats {
        var dataSent = 0L
        var packetsSent = 0L
        var dataReceived = 0L
        var packetsReceived = 0L
        val dataStats = getVpnDataStats(midnight).firstOrNull() ?: emptyList()

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

    data class DataStats(val sent: DataTransfer, val received: DataTransfer)
    data class DataTransfer(val dataSize: Long, val numberPackets: Long)
}
