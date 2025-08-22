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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.model.dateOfLastWeek
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.feature.removal.VpnFeatureRemover
import com.duckduckgo.mobile.android.vpn.network.ExternalVpnDetector
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesViewModel(ActivityScope::class)
class DeviceShieldTrackerActivityViewModel @Inject constructor(
    private val deviceShieldPixels: DeviceShieldPixels,
    private val appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository,
    private val vpnStateMonitor: VpnStateMonitor,
    private val vpnDetector: ExternalVpnDetector,
    private val vpnFeatureRemover: VpnFeatureRemover,
    private val vpnStore: VpnStore,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    private val refreshVpnRunningState = MutableStateFlow(System.currentTimeMillis())
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private var lastVpnRequestTime = -1L

    internal suspend fun getRunningState(): Flow<VpnState> = withContext(dispatcherProvider.io()) {
        return@withContext vpnStateMonitor
            .getStateFlow(AppTpVpnFeature.APPTP_VPN)
            // we only cared about enabled and disabled states for AppTP
            .filter { (it.state == VpnStateMonitor.VpnRunningState.ENABLED) || (it.state == VpnStateMonitor.VpnRunningState.DISABLED) }
            .combine(refreshVpnRunningState.asStateFlow()) { state, _ -> state }
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
        viewModelScope.launch(dispatcherProvider.io()) {
            when {
                enabled && vpnDetector.isExternalVpnDetected() -> sendCommand(Command.ShowVpnConflictDialog)
                enabled == true -> sendCommand(Command.CheckVPNPermission)
                enabled == false -> sendCommand(Command.ShowDisableVpnConfirmationDialog)
            }
            // If the VPN is not started due to any issue, the getRunningState() won't be updated and the toggle is kept (wrongly) in ON state
            // Check after 1 second to ensure this doesn't happen
            delay(TimeUnit.SECONDS.toMillis(1))
            refreshVpnRunningState.emit(System.currentTimeMillis())
        }
    }

    private suspend fun shouldPromoteAlwaysOnOnAppTPEnable(): Boolean {
        return !vpnStateMonitor.isAlwaysOnEnabled() && vpnStateMonitor.vpnLastDisabledByAndroid()
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

    fun showAppTpEnabledCtaIfNeeded() {
        if (!vpnStore.didShowAppTpEnabledCta()) {
            vpnStore.appTpEnabledCtaDidShow()
            sendCommand(Command.ShowAppTpEnabledCta)
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
            ViewEvent.LaunchDeviceShieldFAQ -> sendCommand(Command.LaunchDeviceShieldFAQ)
            ViewEvent.LaunchExcludedApps -> launchExcludedApps()
            ViewEvent.LaunchMostRecentActivity -> sendCommand(Command.LaunchMostRecentActivity)
            ViewEvent.RemoveFeature -> removeFeature()
            ViewEvent.AskToRemoveFeature -> sendCommand(Command.ShowRemoveFeatureConfirmationDialog)
            ViewEvent.StartVpn -> launchVpn()
            ViewEvent.PromoteAlwaysOnOpenSettings -> onOpenSettingsPromoteAlwaysOnDialog()
            ViewEvent.PromoteAlwaysOnCancelled -> onAlwaysOnPromotionDialogCancelled()
            is ViewEvent.AlwaysOnInitialState -> onAlwaysOnInitialState(viewEvent.alwaysOnState)
            ViewEvent.LaunchTrackingProtectionExclusionListActivity -> sendCommand(Command.LaunchTrackingProtectionExclusionListActivity)
        }
    }

    private fun launchVpn() {
        sendCommand(Command.LaunchVPN)
        viewModelScope.launch(dispatcherProvider.io()) {
            if (shouldPromoteAlwaysOnOnAppTPEnable()) {
                deviceShieldPixels.didShowPromoteAlwaysOnDialog()
                sendCommand(Command.ShowAlwaysOnPromotionDialog)
            }
        }

        deviceShieldPixels.enableFromSummaryTrackerActivity()
    }

    fun removeFeature() {
        deviceShieldPixels.didChooseToRemoveTrackingProtectionFeature()
        vpnFeatureRemover.manuallyRemoveFeature()
        sendCommand(Command.StopVPN)
        sendCommand(Command.CloseScreen)
    }

    private fun onOpenSettingsPromoteAlwaysOnDialog() {
        deviceShieldPixels.didChooseToOpenSettingsFromPromoteAlwaysOnDialog()
        sendCommand(Command.OpenVpnSettings)
    }

    private fun onAlwaysOnPromotionDialogCancelled() {
        // noop
    }

    private fun onAlwaysOnInitialState(alwaysOnState: VpnStateMonitor.AlwaysOnState) {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (alwaysOnState.enabled && alwaysOnState.lockedDown) {
                sendCommand(Command.ShowAlwaysOnLockdownWarningDialog)
            }
        }
    }

    internal data class TrackerActivityViewState(
        val trackerCountInfo: TrackerCountInfo,
        val runningState: VpnState,
    )

    internal data class TrackerCountInfo(
        val trackers: TrackerCount,
        val apps: TrackingAppCount,
    ) {
        fun stringTrackerCount(): String {
            return String.format(Locale.US, "%,d", trackers.value)
        }

        fun stringAppsCount(): String {
            return String.format(Locale.US, "%,d", apps.value)
        }
    }

    sealed class ViewEvent {
        data object LaunchExcludedApps : ViewEvent()
        data object LaunchDeviceShieldFAQ : ViewEvent()
        data object LaunchAppTrackersFAQ : ViewEvent()
        data object LaunchMostRecentActivity : ViewEvent()
        data object LaunchTrackingProtectionExclusionListActivity : ViewEvent()
        data object RemoveFeature : ViewEvent()
        data object StartVpn : ViewEvent()
        data object AskToRemoveFeature : ViewEvent()

        data object PromoteAlwaysOnOpenSettings : ViewEvent()
        data object PromoteAlwaysOnCancelled : ViewEvent()
        data class AlwaysOnInitialState(val alwaysOnState: VpnStateMonitor.AlwaysOnState) : ViewEvent()
    }

    sealed class Command {
        data object StopVPN : Command()
        data object LaunchVPN : Command()
        data object CheckVPNPermission : Command()
        data object VPNPermissionNotGranted : Command()
        data class RequestVPNPermission(val vpnIntent: Intent) : Command()
        data object LaunchManageAppsProtection : Command()
        data object LaunchDeviceShieldFAQ : Command()
        data object LaunchAppTrackersFAQ : Command()
        data object LaunchMostRecentActivity : Command()
        data object LaunchTrackingProtectionExclusionListActivity : Command()
        data object ShowDisableVpnConfirmationDialog : Command()
        data object ShowVpnConflictDialog : Command()
        data object ShowVpnAlwaysOnConflictDialog : Command()
        data object ShowAlwaysOnPromotionDialog : Command()
        data object ShowAlwaysOnLockdownWarningDialog : Command()
        data object ShowRemoveFeatureConfirmationDialog : Command()
        data object CloseScreen : Command()
        data object OpenVpnSettings : Command()
        data object ShowAppTpEnabledCta : Command()
    }
}

internal inline class TrackerCount(val value: Int)
internal inline class TrackingAppCount(val value: Int)
