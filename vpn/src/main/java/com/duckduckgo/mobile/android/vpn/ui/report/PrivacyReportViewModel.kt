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

package com.duckduckgo.mobile.android.vpn.ui.report

import android.content.Context
import androidx.lifecycle.*
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.model.dateOfLastWeek
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import dummy.ui.VpnPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

class PrivacyReportViewModel(
    private val repository: AppTrackerBlockingStatsRepository,
    private val vpnPreferences: VpnPreferences,
    private val applicationContext: Context,
) : ViewModel(), LifecycleObserver {

    private var vpnUpdateJob: Job? = null
    private val _vpnRunning = MutableLiveData<Boolean>()

    val vpnRunning: LiveData<Boolean>
        get() = _vpnRunning

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun pollDeviceShieldState() {
        vpnUpdateJob?.cancel()
        vpnUpdateJob = viewModelScope.launch {
            while (isActive) {
                _vpnRunning.value = TrackerBlockingVpnService.isServiceRunning(applicationContext)
                delay(1_000)
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stopPollingDeviceShieldState() {
        vpnUpdateJob?.cancel()
    }

    fun getReport(): LiveData<PrivacyReportView.State.TrackersBlocked> {
        return repository.getVpnTrackers({ dateOfLastWeek() }).map { trackers ->
            val trackerCompanies: MutableList<PrivacyReportView.CompanyTrackers> = mutableListOf()
            val perCompany = trackers.groupBy { it.trackerCompanyId }
            val totalCompanies = perCompany.size
            perCompany.values.forEach {
                trackerCompanies.add(PrivacyReportView.CompanyTrackers.group(it))
            }
            PrivacyReportView.State.TrackersBlocked(totalCompanies, trackers.size, trackerCompanies)
        }.asLiveData()
    }

    fun getDebugLoggingPreference(): Boolean = vpnPreferences.getDebugLoggingPreference()
    fun useDebugLogging(debugLoggingEnabled: Boolean) = vpnPreferences.updateDebugLoggingPreference(debugLoggingEnabled)
    fun isCustomDnsServerSet(): Boolean = vpnPreferences.isCustomDnsServerSet()
    fun useCustomDnsServer(enabled: Boolean) = vpnPreferences.useCustomDnsServer(enabled)

    object PrivacyReportView {
        sealed class State {
            data class TrackersBlocked(val totalCompanies: Int, val totalTrackers: Int, val companiesBlocked: List<CompanyTrackers>) : State()
        }
        data class CompanyTrackers(val companyName: String, val totalTrackers: Int, val lastTracker: VpnTracker) {
            companion object {
                fun group(trackers: List<VpnTracker>): CompanyTrackers {
                    val lastTracker = trackers.first()
                    return CompanyTrackers(lastTracker.company, trackers.size, lastTracker)
                }
            }
        }
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class PrivacyReportViewModelFactory @Inject constructor(
    private val appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository,
    private val vpnPreferences: VpnPreferences,
    private val context: Context
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(PrivacyReportViewModel::class.java) -> (PrivacyReportViewModel(appTrackerBlockingStatsRepository, vpnPreferences, context) as T)
                else -> null
            }
        }
    }
}
