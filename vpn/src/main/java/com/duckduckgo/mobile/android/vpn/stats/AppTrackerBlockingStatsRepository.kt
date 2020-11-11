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

package com.duckduckgo.mobile.android.vpn.stats

import androidx.lifecycle.LiveData
import com.duckduckgo.mobile.android.vpn.model.VpnState
import com.duckduckgo.mobile.android.vpn.model.VpnStats
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import javax.inject.Inject

class AppTrackerBlockingStatsRepository @Inject constructor(private val vpnDatabase: VpnDatabase) {

    fun getVpnState(): VpnState? {
        return vpnDatabase.vpnStateDao().getOneOff()
    }

    fun getVpnStateAsync(): LiveData<VpnState> {
        return vpnDatabase.vpnStateDao().get()
    }

    fun getTodaysTrackersBlockedSync(): List<VpnTrackerAndCompany> {
        val vpnStats = getConnectionStats()
        return if (vpnStats != null) {
            vpnDatabase.vpnTrackerDao().getTrackersAfterSync(vpnStats.startedAt)
        } else {
            emptyList()
        }
    }

    fun getTodaysTrackersBlocked(): LiveData<List<VpnTrackerAndCompany>> {
        val vpnStats = vpnDatabase.vpnStatsDao().getCurrent()!!
        return vpnDatabase.vpnTrackerDao().getTrackersAfter(vpnStats.startedAt)
    }

    fun getConnectionStats(): VpnStats? {
        return vpnDatabase.vpnStatsDao().getCurrent()
    }

}
