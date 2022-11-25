/*
 * Copyright (c) 2021 DuckDuckGo
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
import com.duckduckgo.mobile.android.vpn.model.VpnServiceStateStats
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnServiceStateStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(stat: VpnServiceStateStats)

    @Query("SELECT * FROM vpn_service_state_stats ORDER BY timestamp DESC limit 1")
    fun getLastStateStats(): VpnServiceStateStats?

    @Query("SELECT * FROM vpn_service_state_stats ORDER BY timestamp DESC limit 1")
    fun getStateStats(): Flow<VpnServiceStateStats?>
}
