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

import androidx.room.*
import com.duckduckgo.mobile.android.vpn.model.VpnRunningStats
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnRunningStatsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(stat: VpnRunningStats): Long

    @Query("UPDATE vpn_running_stats SET timeRunningMillis = timeRunningMillis + :timeRunningMillis WHERE id =:id")
    fun updateTimeRunning(
        timeRunningMillis: Long,
        id: String
    )

    @Transaction
    fun upsert(
        timeRunningMillis: Long,
        id: String = bucket()
    ) {
        val runningStats = VpnRunningStats(id, timeRunningMillis)

        // if insert failed, we already have a record so update it instead
        if (insert(runningStats) == -1L) {
            updateTimeRunning(timeRunningMillis, id)
        }
    }

    @Query("SELECT * FROM vpn_running_stats WHERE id >= :startTime AND id < :endTime")
    fun getRunningStatsBetween(
        startTime: String = bucket(),
        endTime: String
    ): Flow<List<VpnRunningStats>>

    private fun bucket() = DatabaseDateFormatter.bucketByHour()
}
