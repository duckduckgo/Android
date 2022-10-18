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

@Dao
interface VpnFeatureRemoverDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(removerState: VpnFeatureRemoverState): Long

    @Query("select * from vpn_feature_remover")
    fun getState(): VpnFeatureRemoverState?
}

@Entity(tableName = "vpn_feature_remover")
data class VpnFeatureRemoverState(
    @PrimaryKey val id: Long = 1,
    val isFeatureRemoved: Boolean,
)
