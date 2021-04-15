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

package com.duckduckgo.mobile.android.vpn.trackers

import androidx.annotation.WorkerThread
import com.duckduckgo.mobile.android.vpn.dao.VpnPreferencesDao
import com.duckduckgo.mobile.android.vpn.model.VpnPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

interface TrackerListProvider {
    @WorkerThread
    fun findTracker(hostname: String): AppTracker?
    fun setUseFullTrackerList(useFullList: Boolean)
}

class RealTrackerListProvider(
    private val vpnPreferencesDao: VpnPreferencesDao,
    private val appTrackerRepository: AppTrackerRepository
) : TrackerListProvider {

    @WorkerThread
    override fun findTracker(hostname: String): AppTracker? {
        return if (shouldUseFullTrackerList()) {
            appTrackerRepository.matchTrackerInFullList(hostname)
        } else {
            appTrackerRepository.matchTrackerInLegacyList(hostname)
        }
    }

    override fun setUseFullTrackerList(useFullList: Boolean) {
        GlobalScope.launch(Dispatchers.IO) {
            vpnPreferencesDao.insert(VpnPreferences(VPN_PREFERENCE_USE_FULL_TRACKER_LIST, useFullList))
        }
    }

    @WorkerThread
    private fun shouldUseFullTrackerList(): Boolean {
        return vpnPreferencesDao.get(VPN_PREFERENCE_USE_FULL_TRACKER_LIST)?.value ?: true
    }

    companion object {
        private const val VPN_PREFERENCE_USE_FULL_TRACKER_LIST = "VPN_PREFERENCE_USE_FULL_TRACKER_LIST"
    }
}
