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

package com.duckduckgo.mobile.android.vpn.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duckduckgo.mobile.android.vpn.model.VpnDataStats
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnDataStatsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(stat: VpnDataStats): Long

    @Query("UPDATE vpn_data_stats SET dataSent = dataSent + :dataSent, packetsSent = packetsSent + 1 WHERE id =:id")
    fun updateDataSent(
        dataSent: Int,
        id: String = DatabaseDateFormatter.bucketByHour()
    )

    @Query("UPDATE vpn_data_stats SET dataReceived = dataReceived + :dataReceived, packetsReceived = packetsReceived + 1 WHERE id =:id")
    fun updateDataReceived(
        dataReceived: Int,
        id: String
    )

    fun upsertDataReceived(
        dataReceived: Int,
        id: String = bucket()
    ) {
        val newStats = VpnDataStats(id = id, dataReceived = dataReceived.toLong(), packetsReceived = 1)

        if (insert(newStats) == -1L) {
            updateDataReceived(dataReceived, id)
        }
    }

    fun upsertDataSent(
        dataSent: Int,
        id: String = bucket()
    ) {
        val newStats = VpnDataStats(id = id, dataSent = dataSent.toLong(), packetsSent = 1)

        if (insert(newStats) == -1L) {
            updateDataSent(dataSent, id)
        }
    }

    @Query("SELECT * FROM vpn_data_stats WHERE id >= :startTime AND id < :endTime")
    fun getDataStatsBetween(
        startTime: String,
        endTime: String
    ): Flow<List<VpnDataStats>>

    private fun bucket() = DatabaseDateFormatter.bucketByHour()
}
