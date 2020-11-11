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

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.mobile.android.vpn.di.DefaultVpnDispatcherProvider
import com.duckduckgo.mobile.android.vpn.di.VpnDispatcherProvider
import com.duckduckgo.mobile.android.vpn.model.TimePassed
import com.duckduckgo.mobile.android.vpn.model.VpnState
import com.duckduckgo.mobile.android.vpn.model.VpnStats
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.temporal.ChronoUnit

class VpnControllerViewModel(
    private val repository: AppTrackerBlockingStatsRepository,
    private val dispatchers: VpnDispatcherProvider = DefaultVpnDispatcherProvider()
) : ViewModel() {

    data class ViewState(
        val uuid: String,
        val isVpnRunning: Boolean,
        val trackersBlocked: String,
        val lastTrackerBlocked: String,
        val connectionStats: VpnStats?,
        val dataSent: String,
        val dataReceived: String
    )

    private lateinit var trackersBlocked: LiveData<List<VpnTrackerAndCompany>>
    private val trackerBlockedObserver = Observer<List<VpnTrackerAndCompany>> { onNewTrackerBlocked(it) }

    private val lastState: LiveData<VpnState> = repository.getVpnStateAsync()
    private val vpnStateObserver = Observer<VpnState> { onStateUpdate() }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()

    @VisibleForTesting
    public override fun onCleared() {
        super.onCleared()
        trackersBlocked.removeObserver(trackerBlockedObserver)
        lastState.removeObserver(vpnStateObserver)
    }

    fun onCreate() {
        if (!lastState.hasActiveObservers()) {
            lastState.observeForever(vpnStateObserver)
        }
        initTrackersObserver()
    }

    private fun initTrackersObserver() {
        viewModelScope.launch {
            val connectionStats = withContext(dispatchers.io()) { repository.getConnectionStats() }
            if (connectionStats != null) {
                trackersBlocked = withContext(dispatchers.io()) { repository.getTodaysTrackersBlocked() }
                trackersBlocked.observeForever(trackerBlockedObserver)
            }
        }
    }

    fun loadData() {
        initTrackersObserver()
        viewModelScope.launch {
            val vpnState = withContext(dispatchers.io()) { repository.getVpnState() } ?: return@launch
            val connectionStats = withContext(dispatchers.io()) { repository.getConnectionStats() } ?: return@launch
            val trackersBlocked = withContext(Dispatchers.IO) { repository.getTodaysTrackersBlockedSync() }

            viewState.value = ViewState(
                vpnState.uuid,
                vpnState.isRunning,
                generateTrackersBlocked(trackersBlocked),
                generateLastTrackerBlocked(trackersBlocked),
                connectionStats,
                generateDataSent(connectionStats),
                generateDataReceived(connectionStats)
            )
        }
    }

    private fun onStateUpdate() {
        loadData()
    }

    private fun onNewTrackerBlocked(trackersBlocked: List<VpnTrackerAndCompany>) {
        if (trackersBlocked.isNotEmpty()) {
            viewModelScope.launch {
                val vpnState = withContext(dispatchers.io()) { repository.getVpnState() } ?: return@launch
                val connectionStats = withContext(dispatchers.io()) { repository.getConnectionStats() }
                val trackersBlocked = withContext(Dispatchers.IO) { repository.getTodaysTrackersBlockedSync() }

                viewState.value = ViewState(
                    vpnState.uuid,
                    vpnState.isRunning,
                    generateTrackersBlocked(trackersBlocked),
                    generateLastTrackerBlocked(trackersBlocked),
                    connectionStats,
                    generateDataSent(connectionStats),
                    generateDataReceived(connectionStats)
                )
            }
        }
    }

    private fun generateTrackersBlocked(trackersBlocked: List<VpnTrackerAndCompany>): String {
        return if (trackersBlocked.isEmpty()) {
            "No companies have been blocked yet"
        } else {
            val lastTrackerBlocked = trackersBlocked.first()
            val timeDifference = lastTrackerBlocked.tracker.timestamp.until(OffsetDateTime.now(), ChronoUnit.MILLIS)
            val timeRunning = TimePassed.fromMilliseconds(timeDifference)
            return "Today, so far we blocked ${trackersBlocked.size} companies from tracking you, " +
                "the last one was ${lastTrackerBlocked.trackerCompany.company} $timeRunning ago"
        }
    }

    private fun generateLastTrackerBlocked(trackersBlocked: List<VpnTrackerAndCompany>): String {
        return if (trackersBlocked.isEmpty()) {
            "No trackers blocked yet"
        } else {
            val lastTrackerBlocked = trackersBlocked.first()
            val timeDifference = lastTrackerBlocked.tracker.timestamp.until(OffsetDateTime.now(), ChronoUnit.MILLIS)
            val timeRunning = TimePassed.fromMilliseconds(timeDifference)
            "Last tracker blocked was ${lastTrackerBlocked.tracker.domain} " +
                "from ${lastTrackerBlocked.trackerCompany.company} $timeRunning ago "
        }
    }

    private fun generateDataSent(connectionStats: VpnStats?): String {
        return if (connectionStats == null) {
            "Nothing sent yet"
        } else {
            "${showData(connectionStats.dataSent)} sent from ${connectionStats.packetsSent} packets"
        }
    }

    private fun generateDataReceived(connectionStats: VpnStats?): String {
        return if (connectionStats == null) {
            "Nothing received yet"
        } else {
            "${showData(connectionStats.dataReceived)} received from ${connectionStats.packetsReceived} packets"
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
