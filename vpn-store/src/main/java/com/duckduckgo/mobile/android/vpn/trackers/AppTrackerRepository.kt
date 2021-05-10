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
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

interface AppTrackerRepository {
    fun matchTrackerInLegacyList(hostname: String) : AppTracker?

    fun matchTrackerInFullList(hostname: String): AppTracker?

    @WorkerThread
    fun getAppExclusionList(): List<String>
}

class RealAppTrackerRepository(
    private val context: Context,
    private val moshi: Moshi,
    private val vpnAppTrackerBlockingDao: VpnAppTrackerBlockingDao
) : AppTrackerRepository {

    override fun matchTrackerInLegacyList(hostname: String): AppTracker? {
        val json = context.resources.openRawResource(R.raw.reduced_app_trackers_blocklist).bufferedReader().use { it.readText() }
        return loadAppTrackers(json).firstOrNull { hostname.endsWith(it.hostname) }
    }

    override fun matchTrackerInFullList(hostname: String): AppTracker? {
        return vpnAppTrackerBlockingDao.getTrackerBySubdomain(hostname)
    }

    override fun getAppExclusionList(): List<String> {
        return vpnAppTrackerBlockingDao.getAppExclusionList().map { it.packageId }
    }

    private fun loadAppTrackers(json: String): List<AppTracker> {
        val adapter : JsonAdapter<JsonAppBlockingList> = moshi.adapter(JsonAppBlockingList::class.java)
        return adapter.fromJson(json)?.trackers.orEmpty()
            .filter { !it.value.isCdn }
            .mapValues {
                AppTracker(
                    hostname = it.key,
                    trackerCompanyId = it.value.owner.displayName.hashCode(),
                    owner = it.value.owner,
                    app = it.value.app,
                    isCdn = it.value.isCdn
                )
        }.map { it.value }
    }
}
