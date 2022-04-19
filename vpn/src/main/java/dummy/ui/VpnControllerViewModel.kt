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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedRepository
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.model.VpnState
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.DataStats
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ContributesViewModel(AppScope::class)
class VpnControllerViewModel @Inject constructor(
    private val appTrackerBlockedRepository: AppTrackerBlockingStatsRepository,
    private val webTrackersBlockedRepository: WebTrackersBlockedRepository,
    private val applicationContext: Context,
    private val vpnPreferences: VpnPreferences
) : ViewModel() {

    fun getRunningTimeUpdates(startTime: () -> String): LiveData<VpnRunningStatus> {
        return appTrackerBlockedRepository.getRunningTimeMillis(startTime)
            .map { timeRunning -> VpnRunningStatus(timeRunning, TrackerBlockingVpnService.isServiceRunning(applicationContext)) }
            .asLiveData()
    }

    fun getDataTransferredUpdates(startTime: () -> String): LiveData<DataStats> {
        return appTrackerBlockedRepository.getVpnDataStats(startTime).asLiveData()
    }

    fun getAppTrackerBlockedUpdates(startTime: () -> String): LiveData<AppTrackersBlocked> {
        return appTrackerBlockedRepository.getVpnTrackers(startTime).map {
            AppTrackersBlocked(it)
        }.asLiveData()
    }

    fun getWebTrackerBlockedUpdates(startTime: () -> String): LiveData<WebTrackersBlocked> {
        return webTrackersBlockedRepository.get(startTime).map {
            WebTrackersBlocked(it)
        }.asLiveData()
    }

    fun getVpnState(): LiveData<VpnState> {
        return appTrackerBlockedRepository.getVpnState().asLiveData()
    }

    fun getDebugLoggingPreference(): Boolean = vpnPreferences.getDebugLoggingPreference()

    data class VpnRunningStatus(
        val runningTimeMillis: Long,
        val isRunning: Boolean
    )

    data class AppTrackersBlocked(val trackerList: List<VpnTracker>) {

        fun byCompany(): Map<Int, List<VpnTracker>> {
            return trackerList.groupBy { it.trackerCompanyId }
        }
    }

    data class WebTrackersBlocked(val trackerList: List<WebTrackerBlocked>) {

        fun byCompany(): Map<String, List<WebTrackerBlocked>> {
            return trackerList.groupBy { it.trackerCompany }
        }
    }
}
