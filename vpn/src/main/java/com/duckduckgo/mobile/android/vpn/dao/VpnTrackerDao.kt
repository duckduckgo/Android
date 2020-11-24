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
import androidx.room.*
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnTrackerDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(tracker: VpnTracker)

    @Transaction
    @Query("select * from vpn_tracker order by timestamp desc limit 1")
    fun getLastTrackerBlockedSync(): VpnTrackerAndCompany?

    @Transaction
    @Query("select * from vpn_tracker order by timestamp desc limit 1")
    fun getLastTrackerBlocked(): LiveData<VpnTrackerAndCompany>

    @Transaction
    @Query("select * from vpn_tracker where timestamp > :startedAt group by trackerCompanyId order by timestamp desc")
    fun getTrackersByCompanyAfterSync(startedAt: String): List<VpnTrackerAndCompany>

    @Transaction
    @Query("select * from vpn_tracker where timestamp >= :startedAt order by timestamp desc")
    fun getTrackersAfterSync(startedAt: String): Flow<List<VpnTrackerAndCompany>>

    @Transaction
    @Query("select * from vpn_tracker where timestamp > :startedAt group by trackerCompanyId order by timestamp desc")
    fun getTrackersByCompanyAfter(startedAt: String): LiveData<List<VpnTrackerAndCompany>>
}
