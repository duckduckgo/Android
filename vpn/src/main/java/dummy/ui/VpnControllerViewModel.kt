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
import com.duckduckgo.mobile.android.vpn.model.VpnStats
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.ChronoUnit
import timber.log.Timber
import java.text.NumberFormat

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
        val connectionStats: VpnStats?,
        val dataSent: String,
        val dataReceived: String
    )
    private val vpnStateObserver = Observer<VpnState> { onStateUpdate() }
    private val lastState: LiveData<VpnState> = repository.getVpnStateAsync()

    private val packetsFormatter = NumberFormat.getInstance()

    val viewState: MutableLiveData<ViewState> = MutableLiveData()

    @VisibleForTesting
    public override fun onCleared() {
        super.onCleared()
        lastState.removeObserver(vpnStateObserver)
    }

    fun onCreate() {
        lastState.observeForever(vpnStateObserver)
        initTrackersObserver()
    }

    private fun initTrackersObserver() {
        viewModelScope.launch(dispatchers.io()) {
            repository.getConnectionStats() ?: return@launch
        }
    }

    fun refreshData() {
        initTrackersObserver()
        viewModelScope.launch(dispatchers.io()) {
            val vpnDbState = repository.getVpnState() ?: return@launch

            val serviceRunning = TrackerBlockingVpnService.isServiceRunning(applicationContext)
            val vpnState = vpnDbState.copy(isRunning = serviceRunning)
            Timber.i("VPN service running? from service: $serviceRunning, from db: ${vpnState.isRunning}")

            val connectionStats = repository.getConnectionStats()
            val trackerCompaniesBlocked = repository.getTodaysCompaniesBlockedSync()
            val trackersBlocked = repository.getTodaysTrackersBlockedSync()

            withContext(dispatchers.main()) {
                viewState.value = ViewState(
                    uuid = vpnState.uuid,
                    isVpnRunning = vpnState.isRunning,
                    trackerCompaniesBlocked = generateTrackerCompaniesBlocked(trackerCompaniesBlocked),
                    trackersBlocked = generateTrackersBlocked(trackersBlocked),
                    lastTrackerBlocked = generateLastTrackerBlocked(trackersBlocked),
                    connectionStats = connectionStats,
                    dataSent = generateDataSent(connectionStats),
                    dataReceived = generateDataReceived(connectionStats)
                )
            }
        }
    }

    private fun onStateUpdate() {
        refreshData()
    }

    private fun generateTrackerCompaniesBlocked(trackersBlocked: List<VpnTrackerAndCompany>): String {
        return if (trackersBlocked.isEmpty()) {
            applicationContext.getString(R.string.vpnTrackerCompaniesNone)
        } else {
            return applicationContext.getString(R.string.vpnTrackerCompaniesBlocked, trackersBlocked.size)
        }
    }

    private fun generateTrackersBlocked(trackersBlocked: List<VpnTrackerAndCompany>): String {
        return if (trackersBlocked.isEmpty()) {
            applicationContext.getString(R.string.vpnTrackersNone)
        } else {
            return applicationContext.getString(R.string.vpnTrackersBlocked, trackersBlocked.size)
        }
    }

    private fun generateLastTrackerBlocked(trackersBlocked: List<VpnTrackerAndCompany>): String {
        return if (trackersBlocked.isEmpty()) {
            "No trackers blocked today"
        } else {
            val lastTrackerBlocked = trackersBlocked.first()
            val timeDifference = lastTrackerBlocked.tracker.timestamp.until(OffsetDateTime.now(), ChronoUnit.MILLIS)
            val timeRunning = TimePassed.fromMilliseconds(timeDifference)
            "Latest tracker blocked: ${lastTrackerBlocked.tracker.domain} " +
                    "from ${lastTrackerBlocked.trackerCompany.company} $timeRunning ago "
        }
    }

    private fun generateDataSent(connectionStats: VpnStats?): String {
        return if (connectionStats == null) {
            "Nothing sent yet"
        } else {
            "${showData(connectionStats.dataSent)} sent from ${packetsFormatter.format(connectionStats.packetsSent)} packets"
        }
    }

    private fun generateDataReceived(connectionStats: VpnStats?): String {
        return if (connectionStats == null) {
            "Nothing received yet"
        } else {
            "${showData(connectionStats.dataReceived)} received from ${packetsFormatter.format(connectionStats.packetsReceived)} packets"
        }
    }

    private fun showData(bytes: Long): String {
        return when {
            bytes > 1000000000 -> {
                val gb = bytes / 1000000000
                "$gb GB"
            }
            bytes > 1000000 -> {
                val mb = bytes / 1000000
                "$mb MB"
            }
            bytes > 1024 -> {
                val kb = bytes / 1024
                "$kb KB"
            }
            else -> {
                "$bytes bytes"
            }
        }
    }
}
