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

@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package com.duckduckgo.mobile.android.vpn.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter

@Entity(
    tableName = "vpn_tracker",
    indices = [Index(value = ["timestamp"])]
)
data class VpnTracker(
    @PrimaryKey(autoGenerate = true) val trackerId: Int = 0,
    val trackerCompanyId: Int,
    val domain: String,
    val company: String,
    val companyDisplayName: String,
    @Embedded val trackingApp: TrackingApp,
    val timestamp: String = DatabaseDateFormatter.timestamp()
)

data class BucketizedVpnTracker(
    val bucket: String,
    @Embedded val vpnTracker: VpnTracker
)

@Entity(tableName = "vpn_state")
data class VpnState(
    @PrimaryKey val id: Long = 1,
    val uuid: String
)

@Entity(tableName = "vpn_data_stats")
data class VpnDataStats(
    @PrimaryKey val id: String = DatabaseDateFormatter.bucketByHour(),
    val dataSent: Long = 0,
    val dataReceived: Long = 0,
    val packetsSent: Int = 0,
    val packetsReceived: Int = 0
)

@Entity(tableName = "vpn_running_stats")
data class VpnRunningStats(
    @PrimaryKey val id: String,
    val timeRunningMillis: Long
)

enum class VpnServiceState {
    ENABLED, DISABLED, INVALID
}

@Entity(tableName = "vpn_service_state_stats")
data class VpnServiceStateStats(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: String = DatabaseDateFormatter.timestamp(),
    val state: VpnServiceState
)

data class BucketizedVpnServiceStateStats(
    val day: String,
    @Embedded val vpnServiceStateStats: VpnServiceStateStats
)

@Entity(tableName = "vpn_preferences")
data class VpnPreferences(
    @PrimaryKey val preference: String,
    val value: Boolean
)

data class TrackingApp(val packageId: String, val appDisplayName: String) {
    override fun toString(): String = "package=$packageId ($appDisplayName)"
}
