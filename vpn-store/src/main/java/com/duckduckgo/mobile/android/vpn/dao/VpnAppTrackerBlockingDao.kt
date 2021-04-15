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
import com.duckduckgo.mobile.android.vpn.trackers.AppTracker

@Dao
interface VpnAppTrackerBlockingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracker: List<AppTracker>)

    @Query("SELECT * FROM vpn_app_tracker_bocking WHERE :subdomain LIKE '%' || hostname LIMIT 1")
    fun getTrackerBySubdomain(subdomain: String): AppTracker?
}
