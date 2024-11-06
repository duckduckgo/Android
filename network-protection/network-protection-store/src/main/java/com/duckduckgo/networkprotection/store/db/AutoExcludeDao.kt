/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.store.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoExcludeDao {
    @Query("SELECT * from vpn_flagged_auto_excluded_apps")
    fun getFlaggedIncompatibleApps(): List<FlaggedIncompatibleApp>

    @Query("SELECT * from vpn_flagged_auto_excluded_apps")
    fun getFlaggedIncompatibleAppsFlow(): Flow<List<FlaggedIncompatibleApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFlaggedIncompatibleApps(app: FlaggedIncompatibleApp)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFlaggedIncompatibleApps(app: List<FlaggedIncompatibleApp>)

    @Query("DELETE from vpn_flagged_auto_excluded_apps")
    fun deleteFlaggedIncompatibleApps()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAutoExcludeApps(tracker: List<VpnIncompatibleApp>)

    @Transaction
    fun upsertAutoExcludeApps(
        autoExcludeApps: List<VpnIncompatibleApp>,
    ) {
        deleteAutoExcludeApps()
        insertAutoExcludeApps(autoExcludeApps)
    }

    @Query("DELETE from vpn_auto_excluded_apps")
    fun deleteAutoExcludeApps()

    @Query("SELECT * FROM vpn_auto_excluded_apps")
    fun getAutoExcludeApps(): List<VpnIncompatibleApp>
}
