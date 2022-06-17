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

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.model.dateOfLastWeek
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.feature.removal.VpnFeatureRemover
import com.duckduckgo.mobile.android.vpn.network.VpnDetector
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class DeviceShieldTrackerActivityViewModel @Inject constructor(
    private val deviceShieldPixels: DeviceShieldPixels,
    private val appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository,
    private val vpnStateMonitor: VpnStateMonitor,
    private val vpnDetector: VpnDetector,
    private val vpnFeatureRemover: VpnFeatureRemover,
    private val vpnStore: VpnStore,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private var lastVpnRequestTime = -1L

    internal suspend fun getRunningState(): Flow<VpnState> = withContext(dispatcherProvider.io()) {
        return@withContext vpnStateMonitor.getStateFlow()
    }

    internal suspend fun getTrackingAppsCount(): Flow<TrackingAppCount> = withContext(dispatcherProvider.io()) {
        return@withContext appTrackerBlockingStatsRepository.getTrackingAppsCountBetween({ dateOfLastWeek() })
            .map { TrackingAppCount(it) }
    }

    internal suspend fun getBlockedTrackersCount(): Flow<TrackerCount> = withContext(dispatcherProvider.io()) {
        return@withContext appTrackerBlockingStatsRepository.getBlockedTrackersCountBetween({ dateOfLastWeek() })
            .map { TrackerCount(it) }
    }

    internal fun onAppTPToggleSwitched(enabled: Boolean) {
        when {
            enabled && vpnDetector.isVpnDetected() -> sendCommand(Command.ShowVpnConflictDialog)
            enabled == true -> sendCommand(Command.CheckVPNPermission)
            enabled == false -> sendCommand(Command.ShowDisableVpnConfirmationDialog)
        }
    }

    private fun shouldPromoteAlwaysOn(): Boolean {
        val shouldPromoteAlwaysOn =
            vpnStore.getAppTPManuallyEnables() >= VpnStore.ALWAYS_ON_PROMOTION_DELTA &&
                vpnStore.userAllowsShowPromoteAlwaysOn() &&
                !vpnStore.isAlwaysOnEnabled()

        if (shouldPromoteAlwaysOn) {
            vpnStore.resetAppTPManuallyEnablesCounter()
        }

        return shouldPromoteAlwaysOn
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }

    fun onVPNPermissionNeeded(permissionIntent: Intent) {
        lastVpnRequestTime = System.currentTimeMillis()
        sendCommand(Command.RequestVPNPermission(permissionIntent))
    }

    fun onVPNPermissionResult(resultCode: Int) {
        when (resultCode) {
            AppCompatActivity.RESULT_OK -> {
                launchVpn()
                return
            }
            else -> {
                if (System.currentTimeMillis() - lastVpnRequestTime < 1000) {
                    sendCommand(Command.ShowVpnAlwaysOnConflictDialog)
                } else {
                    sendCommand(Command.VPNPermissionNotGranted)
                }
                lastVpnRequestTime = -1
            }
        }
    }

    internal fun launchExcludedApps() {
        sendCommand(Command.LaunchManageAppsProtection)
    }

    internal fun onAppTpManuallyDisabled() {
        deviceShieldPixels.disableFromSummaryTrackerActivity()
        viewModelScope.launch {
            command.send(Command.StopVPN)
        }
    }

    internal fun onViewEvent(viewEvent: ViewEvent) {
        when (viewEvent) {
            ViewEvent.LaunchAppTrackersFAQ -> sendCommand(Command.LaunchAppTrackersFAQ)
            ViewEvent.LaunchBetaInstructions -> {
                deviceShieldPixels.didOpenBetaInstructions()
                sendCommand(Command.LaunchBetaInstructions)
            }
            ViewEvent.LaunchDeviceShieldFAQ -> sendCommand(Command.LaunchDeviceShieldFAQ)
            ViewEvent.LaunchExcludedApps -> launchExcludedApps()
            ViewEvent.LaunchMostRecentActivity -> sendCommand(Command.LaunchMostRecentActivity)
            ViewEvent.RemoveFeature -> removeFeature()
            ViewEvent.AskToRemoveFeature -> sendCommand(Command.ShowRemoveFeatureConfirmationDialog)
            ViewEvent.StartVpn -> launchVpn()
            ViewEvent.PromoteAlwaysOnForget -> onForgetPromoteAlwaysOnDialog()
            ViewEvent.PromoteAlwaysOnRemindLater -> onRemindLaterPromoteAlwaysOnDialog()
            ViewEvent.PromoteAlwaysOnOpenSettings -> onOpenSettingsPromoteAlwaysOnDialog()
        }

    }

    private fun launchVpn() {
        sendCommand(Command.LaunchVPN)
        if (shouldPromoteAlwaysOn()) {
            deviceShieldPixels.didShowPromoteAlwaysOnDialog()
            sendCommand(Command.ShowAlwaysOnPromotionDialog)
        }

        deviceShieldPixels.enableFromSummaryTrackerActivity()
        vpnStore.onAppTPManuallyEnabled()
    }

    fun removeFeature() {
        deviceShieldPixels.didChooseToRemoveTrackingProtectionFeature()
        vpnFeatureRemover.manuallyRemoveFeature()
        sendCommand(Command.StopVPN)
        sendCommand(Command.CloseScreen)
    }

    private fun onForgetPromoteAlwaysOnDialog() {
        deviceShieldPixels.didChooseToForgetPromoteAlwaysOnDialog()
        vpnStore.onForgetPromoteAlwaysOn()
    }

    private fun onRemindLaterPromoteAlwaysOnDialog() {
        deviceShieldPixels.didChooseToDismissPromoteAlwaysOnDialog()
    }

    private fun onOpenSettingsPromoteAlwaysOnDialog() {
        deviceShieldPixels.didChooseToOpenSettingsFromPromoteAlwaysOnDialog()
        sendCommand(Command.OpenVpnSettings)
    }

    internal data class TrackerActivityViewState(
        val trackerCountInfo: TrackerCountInfo,
        val runningState: VpnState
    )

    internal data class TrackerCountInfo(
        val trackers: TrackerCount,
        val apps: TrackingAppCount
    ) {
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
        object RemoveFeature : ViewEvent()
        object StartVpn : ViewEvent()
        object AskToRemoveFeature : ViewEvent()

        object PromoteAlwaysOnForget : ViewEvent()
        object PromoteAlwaysOnRemindLater : ViewEvent()

        object PromoteAlwaysOnOpenSettings : ViewEvent()
    }

    sealed class Command {
        object StopVPN : Command()
        object LaunchVPN : Command()
        object CheckVPNPermission : Command()
        object VPNPermissionNotGranted : Command()
        data class RequestVPNPermission(val vpnIntent: Intent) : Command()
        object LaunchManageAppsProtection : Command()
        object LaunchDeviceShieldFAQ : Command()
        object LaunchAppTrackersFAQ : Command()
        object LaunchBetaInstructions : Command()
        object LaunchMostRecentActivity : Command()
        object ShowDisableVpnConfirmationDialog : Command()
        object ShowVpnConflictDialog : Command()
        object ShowVpnAlwaysOnConflictDialog : Command()
        object ShowAlwaysOnPromotionDialog : Command()
        object ShowRemoveFeatureConfirmationDialog : Command()
        object CloseScreen : Command()
        object OpenVpnSettings : Command()
    }
}

internal inline class TrackerCount(val value: Int)
internal inline class TrackingAppCount(val value: Int)
