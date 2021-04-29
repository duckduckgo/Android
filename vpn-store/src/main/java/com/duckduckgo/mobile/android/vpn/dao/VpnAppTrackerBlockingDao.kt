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
import com.duckduckgo.mobile.android.vpn.trackers.AppTracker
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerMetadata

@Dao
interface VpnAppTrackerBlockingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTrackerBlocklist(tracker: List<AppTracker>)

    @Query("SELECT * FROM vpn_app_tracker_blocking_list WHERE :subdomain LIKE '%' || hostname LIMIT 1")
    fun getTrackerBySubdomain(subdomain: String): AppTracker?

    @Query("SELECT * from vpn_app_tracker_blocking_list_metadata ORDER BY id DESC LIMIT 1")
    fun getTrackerBlocklistMetadata() : AppTrackerMetadata?

    @Insert
    fun setTrackerBlocklistMetadata(appTrackerMetadata: AppTrackerMetadata)

    @Query("DELETE from vpn_app_tracker_blocking_list")
    fun deleteTrackerBlockList()

    @Transaction
    fun updateTrackerBlocklist(blocklist: List<AppTracker>, metadata: AppTrackerMetadata) {
        setTrackerBlocklistMetadata(metadata)
        deleteTrackerBlockList()
        insertTrackerBlocklist(blocklist)
    }
}
