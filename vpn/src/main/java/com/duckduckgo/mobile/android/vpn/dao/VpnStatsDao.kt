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

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.duckduckgo.mobile.android.vpn.model.VpnStats
import org.threeten.bp.OffsetDateTime

@Dao
interface VpnStatsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(stat: VpnStats)

    @Query("UPDATE vpn_stats SET lastUpdated = :lastUpdated WHERE id =:id")
    fun updateLastUpdated(lastUpdated: OffsetDateTime, id: Long)

    @Query("UPDATE vpn_stats SET dataSent = dataSent + :dataSent, packetsSent = packetsSent + 1 WHERE id =:id")
    fun updateDataSent(dataSent: Int, id: Long)

    @Query("UPDATE vpn_stats SET dataReceived = dataReceived + :dataReceived, packetsReceived = packetsReceived + 1 WHERE id =:id")
    fun updateDataReceived(dataReceived: Int, id: Long)

    @Query("UPDATE vpn_stats SET timeRunning = timeRunning + :timeRunning, lastUpdated = :lastUpdated WHERE id =:id")
    fun updateTimeRunning(timeRunning: Long, lastUpdated: OffsetDateTime, id: Long)

    @Query("select * from vpn_stats order by lastUpdated desc limit 1")
    fun getCurrent(): VpnStats?

    @Query("select * from vpn_stats order by lastUpdated desc limit 1")
    fun observeCurrent(): LiveData<VpnStats>

    @Query("select * from vpn_stats where id=:id ")
    fun get(id: Long): LiveData<VpnStats>

}
