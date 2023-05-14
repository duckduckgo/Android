/*
 * Copyright (c) 2022 DuckDuckGo
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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppTrackerRepository : AppTrackerRepository {
    // list of app IDs
    var appExclusionList: MutableMap<String, String> = mutableMapOf()
        set(value) {
            field = value
            exclusionListFlow.value = getAppExclusionList()
        }

    // list of app IDs
    var systemAppOverrides: Set<String> = setOf()

    // appID -> isProtected
    var manualExclusionList: MutableMap<String, Boolean> = mutableMapOf()
        set(value) {
            field = value
            manualExclusionListFlow.value = getManualAppExclusionList()
        }

    // tracker -> app IDs
    var blocklist: MutableMap<String, Set<String>> = mutableMapOf()
    private val exclusionListFlow = MutableStateFlow(listOf<AppTrackerExcludedPackage>())
    private val manualExclusionListFlow = MutableStateFlow(listOf<AppTrackerManualExcludedApp>())

    override fun findTracker(hostname: String, packageName: String): AppTrackerType {
        return if (blocklist[hostname]?.contains(packageName) == true) {
            hostname.asThirdPartyAppTrackerType()
        } else {
            AppTrackerType.NotTracker
        }
    }

    override fun getAppExclusionList(): List<AppTrackerExcludedPackage> {
        return appExclusionList.map { AppTrackerExcludedPackage(it.key, it.value) }
    }

    override fun getAppExclusionListFlow(): Flow<List<AppTrackerExcludedPackage>> {
        return exclusionListFlow
    }

    override fun getManualAppExclusionList(): List<AppTrackerManualExcludedApp> {
        return manualExclusionList.map { AppTrackerManualExcludedApp(it.key, it.value) }
    }

    override fun getManualAppExclusionListFlow(): Flow<List<AppTrackerManualExcludedApp>> {
        return manualExclusionListFlow
    }

    override fun getSystemAppOverrideList(): List<AppTrackerSystemAppOverridePackage> {
        return systemAppOverrides.map { AppTrackerSystemAppOverridePackage(it) }
    }

    override fun manuallyExcludedApp(packageName: String) {
        manualExclusionList[packageName] = false
    }

    override fun manuallyExcludedApps(packageNames: List<String>) {
        packageNames.forEach { manualExclusionList[it] = false }
    }

    override fun manuallyEnabledApp(packageName: String) {
        manualExclusionList[packageName] = true
    }

    override fun restoreDefaultProtectedList() {
        manualExclusionList = mutableMapOf()
    }

    private fun String.asThirdPartyAppTrackerType(): AppTrackerType {
        return AppTrackerType.ThirdParty(
            AppTracker(
                hostname = this,
                trackerCompanyId = this.hashCode(),
                isCdn = false,
                owner = TrackerOwner(
                    name = this,
                    displayName = this,
                ),
                app = TrackerApp(1, 1.0),
            ),
        )
    }
}
