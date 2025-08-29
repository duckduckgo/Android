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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.TimeDiffFormatter
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository.ProtectionState
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository.ProtectionState.PROTECTED
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository.ProtectionState.UNPROTECTED
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository.ProtectionState.UNPROTECTED_THROUGH_NETP
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerWithEntity
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.model.TrackingSignal
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesViewModel(ActivityScope::class)
class AppTPCompanyTrackersViewModel @Inject constructor(
    private val statsRepository: AppTrackerBlockingStatsRepository,
    private val excludedAppsRepository: TrackingProtectionAppsRepository,
    private val timeDiffFormatter: TimeDiffFormatter,
    private val deviceShieldPixels: DeviceShieldPixels,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val trackerCompanies = mutableMapOf<String, List<TrackingSignal>>()

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private val viewStateFlow = MutableStateFlow(ViewState())
    fun viewState(): StateFlow<ViewState> {
        return viewStateFlow
    }

    suspend fun loadData(
        date: String,
        packageName: String,
    ) {
        withContext(dispatchers.io()) {
            statsRepository
                .getTrackersForAppFromDate(date, packageName)
                .map { aggregateDataPerApp(it, packageName) }
                .flowOn(dispatchers.io())
                .collectLatest { state ->
                    viewStateFlow.emit(state)
                }
        }
    }

    private suspend fun aggregateDataPerApp(
        trackerData: List<VpnTrackerWithEntity>,
        packageName: String,
    ): ViewState {
        val sourceData = mutableListOf<CompanyTrackingDetails>()

        val lastTrackerBlockedAgo =
            if (trackerData.isNotEmpty()) {
                timeDiffFormatter.formatTimePassed(
                    LocalDateTime.now(),
                    LocalDateTime.parse(trackerData[0].tracker.timestamp),
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
                    trackingAttempts = data.value.sumOf { it.tracker.count },
                    timestamp = timestamp,
                    trackingSignals = trackingSignals,
                ),
            )
        }

        val protectionState = excludedAppsRepository.getAppProtectionStatus(packageName)

        return viewStateFlow.value.copy(
            totalTrackingAttempts = sourceData.sumOf { it.trackingAttempts },
            lastTrackerBlockedAgo = lastTrackerBlockedAgo,
            trackingCompanies = sourceData,
            toggleChecked = protectionState == PROTECTED,
            bannerState = protectionState.getBannerState(),
            toggleEnabled = protectionState != UNPROTECTED_THROUGH_NETP,
        )
    }

    private fun ProtectionState.getBannerState(): BannerState {
        return when (this) {
            PROTECTED -> BannerState.NONE
            UNPROTECTED -> BannerState.SHOW_UNPROTECTED
            UNPROTECTED_THROUGH_NETP -> BannerState.SHOW_UNPROTECTED_THROUGH_NETP
        }
    }

    private fun mapTrackingSignals(signals: List<String>): List<TrackingSignal> {
        val originalTrackingSignals = signals.map { TrackingSignal.fromTag(it) }
        val randomElements = originalTrackingSignals.asSequence().shuffled().toList()
        return randomElements.distinctBy { it.signalDisplayName }
    }

    fun onAppPermissionToggled(
        checked: Boolean,
        packageName: String,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            withContext(dispatchers.io()) {
                if (checked) {
                    deviceShieldPixels.didEnableAppProtectionFromDetail()
                    excludedAppsRepository.manuallyEnabledApp(packageName)
                } else {
                    deviceShieldPixels.didDisableAppProtectionFromDetail()
                    excludedAppsRepository.manuallyExcludeApp(packageName)
                }
                command.send(Command.RestartVpn)
                val protectionState = excludedAppsRepository.getAppProtectionStatus(packageName)

                viewStateFlow.emit(
                    viewStateFlow.value.copy(
                        toggleChecked = protectionState == PROTECTED,
                        bannerState = protectionState.getBannerState(),
                        toggleEnabled = protectionState != UNPROTECTED_THROUGH_NETP,
                    ),
                )
            }
        }
    }

    data class ViewState(
        val totalTrackingAttempts: Int = 0,
        val lastTrackerBlockedAgo: String = "",
        val trackingCompanies: List<CompanyTrackingDetails> = emptyList(),
        val toggleChecked: Boolean = false,
        val bannerState: BannerState = BannerState.NONE,
        val toggleEnabled: Boolean = true,
    )

    internal sealed class Command {
        data object RestartVpn : Command()
    }

    enum class BannerState {
        NONE,
        SHOW_UNPROTECTED,
        SHOW_UNPROTECTED_THROUGH_NETP,
    }

    data class CompanyTrackingDetails(
        val companyName: String,
        val companyDisplayName: String,
        val trackingAttempts: Int,
        val timestamp: String,
        val trackingSignals: List<TrackingSignal>,
        val expanded: Boolean = false,
    )
}
