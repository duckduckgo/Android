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
interface VpnNotificationsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(notification: VpnNotification): Long

    @Query("select * from vpn_notification where id=:id")
    fun get(id: Int): VpnNotification

    @Query("select count(1) > 0 from vpn_notification where id = :id")
    fun exists(id: Int): Boolean

    @Query("UPDATE vpn_notification SET timesRun = timesRun + 1 WHERE id =:id")
    fun increment(id: Int)
}


@Entity(tableName = "vpn_notification")
data class VpnNotification(
    @PrimaryKey val id: Int,
    val timesRun: Long = 0
)