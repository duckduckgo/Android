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

@Entity(tableName = "vpn_heartbeat")
data class HeartBeatEntity(
    @PrimaryKey val type: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface VpnHeartBeatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(heartBeatEntity: HeartBeatEntity)

    @Transaction
    fun insertType(type: String): HeartBeatEntity {
        return HeartBeatEntity(type).also {
            insert(it)
        }
    }

    @Query("select * from vpn_heartbeat where type = :type limit 1")
    fun getHearBeat(type: String): HeartBeatEntity?

    @Query("select * from vpn_heartbeat")
    fun hearBeats(): List<HeartBeatEntity>
}
