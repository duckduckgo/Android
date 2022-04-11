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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerCompanySignal
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.app.global.formatters.time.TimeDiffFormatter
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackingSignal
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDateTime

@ContributesViewModel(ActivityScope::class)
class AppTPCompanyTrackersViewModel
@Inject
constructor(
    private val statsRepository: AppTrackerBlockingStatsRepository,
    private val excludedAppsRepository: TrackingProtectionAppsRepository,
    private val timeDiffFormatter: TimeDiffFormatter,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

    private val trackerCompanies = mutableMapOf<String, List<TrackingSignal>>()

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private var manualChanges: Boolean = false

    private val viewStateFlow = MutableStateFlow(ViewState())
    fun viewState(): StateFlow<ViewState> {
        return viewStateFlow
    }

    suspend fun loadData(
        date: String,
        packageName: String
    ) {
        withContext(dispatchers.io()) {
            statsRepository
                .getTrackersForAppFromDate(date, packageName)
                .map { aggregateDataPerApp(it, packageName) }
                .flowOn(Dispatchers.Default)
                .collectLatest { state ->
                    viewStateFlow.emit(state)
                }
        }
    }

    private fun aggregateDataPerApp(
        trackerData: List<VpnTrackerCompanySignal>,
        packageName: String
    ): ViewState {
        val sourceData = mutableListOf<CompanyTrackingDetails>()

        val lastTrackerBlockedAgo =
            if (trackerData.isNotEmpty()) {
                timeDiffFormatter.formatTimePassed(
                    LocalDateTime.now(), LocalDateTime.parse(trackerData[0].tracker.timestamp)
                )
            } else {
                ""
            }

        val trackerCompany =
            trackerData.sortedByDescending { it.trackerEntity.score }.groupBy {
                it.tracker.trackerCompanyId
            }

        trackerCompany.forEach { data ->
            val trackerCompanyName = data.value[0].tracker.company

            val trackerCompanyDisplayName = data.value[0].tracker.companyDisplayName
            val signals = data.value[0].trackerEntity.signals
            val timestamp = data.value[0].tracker.timestamp

            val trackingSignals = if (trackerCompanies.containsKey(trackerCompanyName)) {
                trackerCompanies.get(trackerCompanyName)!!
            } else {
                val randomTrackingSignals = mapTrackingSignals(signals)
                trackerCompanies.put(trackerCompanyName, randomTrackingSignals)
                randomTrackingSignals
            }

            sourceData.add(
                CompanyTrackingDetails(
                    companyName = trackerCompanyName,
                    companyDisplayName = trackerCompanyDisplayName,
                    trackingAttempts = data.value.size,
                    timestamp = timestamp,
                    trackingSignals = trackingSignals
                )
            )
        }

        return viewStateFlow.value.copy(
            totalTrackingAttempts = trackerData.size,
            lastTrackerBlockedAgo = lastTrackerBlockedAgo,
            trackingCompanies = sourceData,
            protectionEnabled = excludedAppsRepository.isAppProtectionEnabled(packageName)
        )
    }

    private fun mapTrackingSignals(signals: List<String>): List<TrackingSignal> {
        val originalTrackingSignals = signals.map { TrackingSignal.fromTag(it) }
        val randomElements = originalTrackingSignals.asSequence().shuffled().toList()
        return randomElements.distinctBy { it.signalDisplayName }
    }

    fun onAppPermissionToggled(
        checked: Boolean,
        packageName: String
    ) {
        viewModelScope.launch {
            withContext(dispatchers.io()) {
                if (checked) {
                    excludedAppsRepository.manuallyEnabledApp(packageName)
                } else {
                    excludedAppsRepository.manuallyExcludedApp(packageName)
                }
            }
            viewStateFlow.emit(viewStateFlow.value.copy(userChangedState = true, manualProtectionState = checked))
        }
    }

    fun onLeavingScreen() {
        viewModelScope.launch {
            if (userMadeChanges()) {
                command.send(Command.RestartVpn)
            }
        }
    }

    fun userMadeChanges(): Boolean {
        val currentState = viewStateFlow.value
        if (currentState.userChangedState) {
            return currentState.manualProtectionState != currentState.protectionEnabled
        } else {
            return false
        }
    }

    data class ViewState(
        val totalTrackingAttempts: Int = 0,
        val lastTrackerBlockedAgo: String = "",
        val trackingCompanies: List<CompanyTrackingDetails> = emptyList(),
        val protectionEnabled: Boolean = false,
        val userChangedState: Boolean = false,
        val manualProtectionState: Boolean = false
    )

    internal sealed class Command {
        object RestartVpn : Command()
    }

    data class CompanyTrackingDetails(
        val companyName: String,
        val companyDisplayName: String,
        val trackingAttempts: Int,
        val timestamp: String,
        val trackingSignals: List<TrackingSignal>,
        val expanded: Boolean = false
    )
}
