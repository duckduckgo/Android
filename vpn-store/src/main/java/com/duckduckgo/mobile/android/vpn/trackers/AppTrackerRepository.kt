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

import android.content.Context
import androidx.annotation.WorkerThread
import com.duckduckgo.mobile.android.vpn.dao.VpnAppTrackerBlockingDao
import com.duckduckgo.mobile.android.vpn.store.R
import com.squareup.moshi.Moshi
import timber.log.Timber

interface AppTrackerRepository {
    fun matchTrackerInLegacyList(hostname: String): AppTrackerType

    fun matchTrackerInFullList(hostname: String, packageName: String): AppTrackerType

    @WorkerThread
    fun getAppExclusionList(): List<String>
}

class RealAppTrackerRepository(
    private val context: Context,
    private val moshi: Moshi,
    private val vpnAppTrackerBlockingDao: VpnAppTrackerBlockingDao
) : AppTrackerRepository {

    override fun matchTrackerInLegacyList(hostname: String): AppTrackerType {
        val json = context.resources.openRawResource(R.raw.reduced_app_trackers_blocklist).bufferedReader().use { it.readText() }
        val tracker = loadAppTrackers(json).first.firstOrNull { hostname.endsWith(it.hostname) }
        return if (tracker == null) {
            AppTrackerType.NotTracker
        } else {
            // For the legacy list, we don't know if the tracker is 1st/3rd party; always assume 3rd party
            AppTrackerType.ThirdParty(tracker)
        }
    }

    override fun matchTrackerInFullList(hostname: String, packageName: String): AppTrackerType {
        val tracker = vpnAppTrackerBlockingDao.getTrackerBySubdomain(hostname) ?: return AppTrackerType.NotTracker
        val entityName = vpnAppTrackerBlockingDao.getEntityByAppPackageId(packageName)
        if (firstPartyTracker(tracker, entityName)) {
            return AppTrackerType.FirstParty(tracker)
        }

        return AppTrackerType.ThirdParty(tracker)
    }

    private fun firstPartyTracker(tracker: AppTracker, entityName: AppTrackerPackage?): Boolean {
        if (entityName == null) return false
        return tracker.owner.name == entityName.entityName
    }

    private fun loadAppTrackers(json: String): Pair<List<AppTracker>, List<AppTrackerPackage>> {
        return AppTrackerJsonParser.parseAppTrackerJson(moshi, json)
    }

    override fun getAppExclusionList(): List<String> {
        return vpnAppTrackerBlockingDao.getAppExclusionList().map { it.packageId }
    }

}
