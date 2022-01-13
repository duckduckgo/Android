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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerSystemAppOverrideListMetadata
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerSystemAppOverridePackage

@Dao
interface VpnAppTrackerSystemAppsOverridesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSystemAppOverrides(tracker: List<AppTrackerSystemAppOverridePackage>)

    @Transaction
    fun upsertSystemAppOverrides(
        systemAppOverrides: List<AppTrackerSystemAppOverridePackage>,
        metadata: AppTrackerSystemAppOverrideListMetadata
    ) {
        setSystemAppOverridesMetadata(metadata)
        deleteSystemAppOverrides()
        insertSystemAppOverrides(systemAppOverrides)
    }

    @Query("DELETE from vpn_app_tracker_system_app_override_list")
    fun deleteSystemAppOverrides()

    @Query("SELECT * FROM vpn_app_tracker_system_app_override_list")
    fun getSystemAppOverrides(): List<AppTrackerSystemAppOverridePackage>

    @Query("SELECT * from vpn_app_tracker_system_app_override_list_metadata ORDER BY id DESC LIMIT 1")
    fun getSystemAppOverridesMetadata(): AppTrackerSystemAppOverrideListMetadata?

    @Insert
    fun setSystemAppOverridesMetadata(systemAppOverridesMetadata: AppTrackerSystemAppOverrideListMetadata)
}
