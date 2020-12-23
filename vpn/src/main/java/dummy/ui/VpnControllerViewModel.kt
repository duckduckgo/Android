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

package dummy.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.duckduckgo.mobile.android.vpn.model.VpnState
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.DataStats
import kotlinx.coroutines.flow.map

class VpnControllerViewModel(
    private val repository: AppTrackerBlockingStatsRepository,
    private val applicationContext: Context,
    private val vpnPreferences: VpnPreferences
) : ViewModel() {

    fun getRunningTimeUpdates(startTime: () -> String): LiveData<VpnRunningStatus> {
        return repository.getRunningTimeMillis(startTime)
            .map { timeRunning -> VpnRunningStatus(timeRunning, TrackerBlockingVpnService.isServiceRunning(applicationContext)) }
            .asLiveData()
    }

    fun getDataTransferredUpdates(startTime: () -> String): LiveData<DataStats> {
        return repository.getVpnDataStats(startTime).asLiveData()
    }

    fun getTrackerBlockedUpdates(startTime: () -> String): LiveData<TrackersBlocked> {
        return repository.getVpnTrackers(startTime).map {
            TrackersBlocked(it)
        }.asLiveData()
    }

    fun getVpnState(): LiveData<VpnState> {
        return repository.getVpnState().asLiveData()
    }

    fun getDebugLoggingPreference(): Boolean = vpnPreferences.getDebugLoggingPreference()
    fun useDebugLogging(debugLoggingEnabled: Boolean) = vpnPreferences.updateDebugLoggingPreference(debugLoggingEnabled)
    fun isCustomDnsServerSet(): Boolean = vpnPreferences.isCustomDnsServerSet()
    fun useCustomDnsServer(enabled: Boolean) = vpnPreferences.useCustomDnsServer(enabled)

    data class VpnRunningStatus(val runningTimeMillis: Long, val isRunning: Boolean)

    data class TrackersBlocked(val trackerList: List<VpnTrackerAndCompany>) {

        fun byCompany(): Map<Int, List<VpnTrackerAndCompany>> {
            return trackerList.groupBy { it.trackerCompany.trackerCompanyId }
        }

    }
}
