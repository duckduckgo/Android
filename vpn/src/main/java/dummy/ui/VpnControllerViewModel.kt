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
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.*
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.di.DefaultVpnDispatcherProvider
import com.duckduckgo.mobile.android.vpn.di.VpnDispatcherProvider
import com.duckduckgo.mobile.android.vpn.model.TimePassed
import com.duckduckgo.mobile.android.vpn.model.VpnState
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.model.dateOfPreviousMidnight
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository.DataTransfer
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDateTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.ChronoUnit

class VpnControllerViewModel(
    private val repository: AppTrackerBlockingStatsRepository,
    private val dispatchers: VpnDispatcherProvider = DefaultVpnDispatcherProvider(),
    private val applicationContext: Context
) : ViewModel() {

    data class ViewState(
        val uuid: String,
        val isVpnRunning: Boolean,
        val trackerCompaniesBlocked: String,
        val trackersBlocked: String,
        val lastTrackerBlocked: String,
        val timeRunningMillis: Long,
        val dataSent: DataTransfer,
        val dataReceived: DataTransfer
    )

    private val vpnStateObserver = Observer<VpnState> { onStateUpdate() }
    private val lastState: LiveData<VpnState> = repository.getVpnStateAsync()

    val viewState: MutableLiveData<ViewState> = MutableLiveData()

    @VisibleForTesting
    public override fun onCleared() {
        super.onCleared()
        lastState.removeObserver(vpnStateObserver)
    }

    fun onCreate() {
        lastState.observeForever(vpnStateObserver)
    }

    fun refreshData() {
        val midnight = dateOfPreviousMidnight()

        viewModelScope.launch(dispatchers.io()) {
            val runtime = repository.getRunningTimeMillis(midnight)
            val vpnState = repository.getVpnState()
            val serviceRunning = TrackerBlockingVpnService.isServiceRunning(applicationContext)

            val trackers = repository.getVpnTrackers(midnight).firstOrNull() ?: emptyList()
            val trackersByCompany = trackers.groupBy { it.trackerCompany.trackerCompanyId }
            val dataTransferredStats = repository.getDataStats(midnight)

            withContext(dispatchers.main()) {
                viewState.value = ViewState(
                    uuid = vpnState.uuid,
                    isVpnRunning = serviceRunning,
                    trackerCompaniesBlocked = generateTrackerCompaniesBlocked(trackersByCompany.size),
                    trackersBlocked = generateTrackersBlocked(trackers.size),
                    lastTrackerBlocked = generateLastTrackerBlocked(trackers),
                    timeRunningMillis = runtime,
                    dataSent = dataTransferredStats.sent,
                    dataReceived = dataTransferredStats.received
                )
            }
        }
    }

    private fun onStateUpdate() {
        refreshData()
    }

    private fun generateTrackerCompaniesBlocked(totalTrackerCompanies: Int): String {
        return if (totalTrackerCompanies == 0) {
            applicationContext.getString(R.string.vpnTrackerCompaniesNone)
        } else {
            return applicationContext.getString(R.string.vpnTrackerCompaniesBlocked, totalTrackerCompanies)
        }
    }

    private fun generateTrackersBlocked(totalTrackers: Int): String {
        return if (totalTrackers == 0) {
            applicationContext.getString(R.string.vpnTrackersNone)
        } else {
            return applicationContext.getString(R.string.vpnTrackersBlocked, totalTrackers)
        }
    }

    private fun generateLastTrackerBlocked(trackersBlocked: List<VpnTrackerAndCompany>): String {
        return if (trackersBlocked.isEmpty()) {
            ""
        } else {
            val lastTrackerBlocked = trackersBlocked.first()
            val timestamp = LocalDateTime.parse(lastTrackerBlocked.tracker.timestamp)
            val timeDifference = timestamp.until(OffsetDateTime.now(), ChronoUnit.MILLIS)
            val timeRunning = TimePassed.fromMilliseconds(timeDifference)
            "Latest tracker blocked ${timeRunning.format()} ago\n${lastTrackerBlocked.tracker.domain}\n(owned by ${lastTrackerBlocked.trackerCompany.company})"
        }
    }
}
