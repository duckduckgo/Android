/*
 * Copyright (c) 2019 DuckDuckGo
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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.apps.DeviceShieldExcludedApps
import com.duckduckgo.mobile.android.vpn.model.dateOfLastWeek
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import dummy.ui.VpnPreferences
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider

class DeviceShieldTrackerActivityViewModel(
    private val applicationContext: Context,
    private val deviceShieldPixels: DeviceShieldPixels,
    private val vpnPreferences: VpnPreferences,
    private val appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository,
    private val deviceShieldExcludedApps: DeviceShieldExcludedApps,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    private val job = ConflatedJob()
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    internal val vpnRunningState = MutableStateFlow(
        RunningState(isRunning = true, 0)
    )

    internal suspend fun getTrackingAppsCount(): Flow<TrackingAppCount> = withContext(dispatcherProvider.io()) {
        return@withContext appTrackerBlockingStatsRepository.getTrackingAppsCountBetween({ dateOfLastWeek() })
            .map { TrackingAppCount(it) }
    }

    internal suspend fun getBlockedTrackersCount(): Flow<TrackerCount> = withContext(dispatcherProvider.io()) {
        return@withContext appTrackerBlockingStatsRepository.getBlockedTrackersCountBetween({ dateOfLastWeek() })
            .map { TrackerCount(it) }
            .onStart { pollDeviceShieldState() }
    }

    private fun pollDeviceShieldState() {
        job += viewModelScope.launch {
            val excludedAppsCount = deviceShieldExcludedApps.getExclusionAppList().filterNot { it.isDdgApp }.size

            while (isActive) {
                val isRunning = TrackerBlockingVpnService.isServiceRunning(applicationContext)
                vpnRunningState.emit(RunningState(isRunning, excludedAppsCount))

                delay(1_000)
            }
        }
    }

    internal fun onDeviceShieldSettingChanged(enabled: Boolean) {

        if (enabled) {
            deviceShieldPixels.enableFromSummaryTrackerActivity()
        } else {
            deviceShieldPixels.disableFromSummaryTrackerActivity()
        }

        viewModelScope.launch {
            if (enabled) {
                command.send(Command.StartDeviceShield)
            } else {
                command.send(Command.StopDeviceShield)
            }
        }
    }

    internal fun onViewEvent(viewEvent: ViewEvent) {
        viewModelScope.launch {
            when (viewEvent) {
                ViewEvent.LaunchAppTrackersFAQ -> {
                    deviceShieldPixels.privacyReportArticleDisplayed()
                    command.send(Command.LaunchAppTrackersFAQ)
                }
                ViewEvent.LaunchBetaInstructions -> command.send(Command.LaunchBetaInstructions)
                ViewEvent.LaunchDeviceShieldFAQ -> command.send(Command.LaunchDeviceShieldFAQ)
                ViewEvent.LaunchExcludedApps -> command.send(Command.LaunchExcludedApps)
                ViewEvent.LaunchMostRecentActivity -> command.send(Command.LaunchMostRecentActivity)
            }
        }
    }

    internal fun isCustomDnsServerSet(): Boolean = vpnPreferences.isCustomDnsServerSet()
    internal fun useCustomDnsServer(enabled: Boolean) = vpnPreferences.useCustomDnsServer(enabled)

    internal data class TrackerActivityViewState(val trackerCountInfo: TrackerCountInfo, val runningState: RunningState)

    internal data class TrackerCountInfo(val trackers: TrackerCount, val apps: TrackingAppCount) {
        fun stringTrackerCount(): String {
            return String.format(Locale.US, "%,d", trackers.value)
        }

        fun stringAppsCount(): String {
            return String.format(Locale.US, "%,d", apps.value)
        }
    }

    sealed class ViewEvent {
        object LaunchExcludedApps : ViewEvent()
        object LaunchDeviceShieldFAQ : ViewEvent()
        object LaunchAppTrackersFAQ : ViewEvent()
        object LaunchBetaInstructions : ViewEvent()
        object LaunchMostRecentActivity : ViewEvent()
    }

    sealed class Command {
        object StartDeviceShield : Command()
        object StopDeviceShield : Command()
        object LaunchExcludedApps : Command()
        object LaunchDeviceShieldFAQ : Command()
        object LaunchAppTrackersFAQ : Command()
        object LaunchBetaInstructions : Command()
        object LaunchMostRecentActivity : Command()
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class PastWeekTrackerActivityViewModelFactory @Inject constructor(
    private val applicationContext: Provider<Context>,
    private val deviceShieldPixels: Provider<DeviceShieldPixels>,
    private val vpnPreferences: Provider<VpnPreferences>,
    private val appTrackerBlockingStatsRepository: Provider<AppTrackerBlockingStatsRepository>,
    private val deviceShieldExcludedApps: Provider<DeviceShieldExcludedApps>,
    private val dispatcherProvider: Provider<DispatcherProvider>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(DeviceShieldTrackerActivityViewModel::class.java) -> (
                    DeviceShieldTrackerActivityViewModel(
                        applicationContext.get(),
                        deviceShieldPixels.get(),
                        vpnPreferences.get(),
                        appTrackerBlockingStatsRepository.get(),
                        deviceShieldExcludedApps.get(),
                        dispatcherProvider.get()
                    ) as T
                    )
                else -> null
            }
        }
    }
}

internal data class RunningState(val isRunning: Boolean, val excludedAppsCount: Int) {
    fun stringExcludedAppsCount(): String {
        return String.format(Locale.US, "%,d", excludedAppsCount)
    }
}
internal inline class TrackerCount(val value: Int)
internal inline class TrackingAppCount(val value: Int)
