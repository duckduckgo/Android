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

import androidx.room.*
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason.UNKNOWN
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerEntity

@Entity(
    tableName = "vpn_tracker",
    indices = [Index(value = ["bucket"])],
    primaryKeys = ["bucket", "domain", "packageId"]
)
data class VpnTracker(
    val trackerCompanyId: Int,
    val domain: String,
    val company: String,
    val companyDisplayName: String,
    @Embedded val trackingApp: TrackingApp,
    val timestamp: String = DatabaseDateFormatter.timestamp(),
    val bucket: String = DatabaseDateFormatter.timestamp().split("T").first(),
    val count: Int = 1,
)

data class BucketizedVpnTracker(
    @Embedded val trackerCompanySignal: VpnTrackerCompanySignal
)

@Entity(tableName = "vpn_state")
data class VpnState(
    @PrimaryKey val id: Long = 1,
    val uuid: String
)

@Entity(tableName = "vpn_running_stats")
data class VpnRunningStats(
    @PrimaryKey val id: String,
    val timeRunningMillis: Long
)

enum class VpnServiceState {
    ENABLED,
    DISABLED,
    INVALID
}

enum class VpnStoppingReason {
    SELF_STOP,
    ERROR,
    REVOKED,
    UNKNOWN
}

data class AlwaysOnState(val alwaysOnEnabled: Boolean, val alwaysOnLockedDown: Boolean) {
    companion object {
        val ALWAYS_ON_DISABLED = AlwaysOnState(alwaysOnEnabled = false, alwaysOnLockedDown = false)
        val ALWAYS_ON_ENABLED = AlwaysOnState(alwaysOnEnabled = true, alwaysOnLockedDown = false)
        val ALWAYS_ON_ENABLED_LOCKED_DOWN = AlwaysOnState(alwaysOnEnabled = true, alwaysOnLockedDown = true)
    }
}

@Entity(tableName = "vpn_service_state_stats")
data class VpnServiceStateStats(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: String = DatabaseDateFormatter.timestamp(),
    val state: VpnServiceState,
    val stopReason: VpnStoppingReason = UNKNOWN,
    @Embedded val alwaysOnState: AlwaysOnState = AlwaysOnState.ALWAYS_ON_DISABLED
)

data class TrackingApp(
    val packageId: String,
    val appDisplayName: String
) {
    override fun toString(): String = "package=$packageId ($appDisplayName)"
}

data class VpnTrackerCompanySignal(
    @Embedded val tracker: VpnTracker,
    @Relation(
        parentColumn = "trackerCompanyId",
        entityColumn = "trackerCompanyId"
    )
    val trackerEntity: AppTrackerEntity
)
