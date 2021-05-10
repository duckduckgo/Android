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

package com.duckduckgo.mobile.android.vpn.trackers

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json

/** This table contains the list of app trackers to be blocked */
@Entity(tableName = "vpn_app_tracker_blocking_list")
data class AppTracker(
    @PrimaryKey val hostname: String,
    val trackerCompanyId: Int,
    @Embedded val owner: TrackerOwner,
    @Embedded val app: TrackerApp,
    val isCdn: Boolean
)

@Entity(tableName = "vpn_app_tracker_blocking_list_metadata")
data class AppTrackerMetadata(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    val eTag: String?
)

@Entity(tableName = "vpn_app_tracker_exclusion_list")
data class AppTrackerExcludedPackage(
    @PrimaryKey val packageId: String
)

@Entity(tableName = "vpn_app_tracker_exclusion_list_metadata")
data class AppTrackerExclusionListMetadata(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    val eTag: String?
)


data class JsonAppBlockingList(
    val trackers: Map<String, JsonAppTracker>
)

class JsonAppTracker(
    val owner: TrackerOwner,
    val app: TrackerApp,
    @field:Json(name = "CDN")
    val isCdn: Boolean
)

data class TrackerOwner(
    val name: String,
    val displayName: String
)

data class TrackerApp(
    val score: Int,
    val prevalence: Double
)

/** JSON Model that represents the app exclusion list */
data class JsonAppTrackerExclusionList(
    val rules: List<String>
)
