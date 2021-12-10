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

@Entity(tableName = "vpn_app_tracker_blocking_app_packages")
data class AppTrackerPackage(
    @PrimaryKey val packageName: String,
    val entityName: String
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

@Entity(tableName = "vpn_app_tracker_exception_rules")
data class AppTrackerExceptionRule(
    @PrimaryKey
    val rule: String,
    val packageNames: List<String>
)

@Entity(tableName = "vpn_app_tracker_exception_rules_metadata")
data class AppTrackerExceptionRuleMetadata(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    val eTag: String?
)

@Entity(tableName = "vpn_app_tracker_manual_exclusion_list")
data class AppTrackerManualExcludedApp(
    @PrimaryKey val packageId: String,
    val isProtected: Boolean
)

@Entity(tableName = "vpn_app_tracker_entities")
data class AppTrackerEntity(
    @PrimaryKey val trackerCompanyId: Int,
    val entityName: String,
    val score: Int,
    val signals: List<String>
)

data class JsonAppBlockingList(
    val version: String,
    val trackers: Map<String, JsonAppTracker>,
    val packageNames: Map<String, String>,
    val entities: Map<String, JsonTrackingSignal>,
)

class JsonAppTracker(
    val owner: TrackerOwner,
    val app: TrackerApp,
    @field:Json(name = "CDN")
    val isCdn: Boolean
)

class JsonTrackingSignal(
    val score: Int,
    val signals: List<String>
)

data class TrackerOwner(
    val name: String,
    val displayName: String
)

data class TrackerApp(
    val score: Int,
    val prevalence: Double
)

sealed class AppTrackerType {
    data class FirstParty(val tracker: AppTracker) : AppTrackerType()
    data class ThirdParty(val tracker: AppTracker) : AppTrackerType()
    object NotTracker : AppTrackerType()
}

data class AppTrackerBlocklist(
    val version: String,
    val trackers: List<AppTracker>,
    val packages: List<AppTrackerPackage>,
    val entities: List<AppTrackerEntity>
)

/** JSON Model that represents the app exclusion list */
data class JsonAppTrackerExclusionList(
    val rules: List<String>
)

/** JSON Model that represents the app tracker rule list */
data class JsonAppTrackerExceptionRules(
    val rules: List<AppTrackerExceptionRule>
)


