/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.management

import android.annotation.SuppressLint
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.network.ExternalVpnDetector
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.AlwaysOnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLING
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.ERROR
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.REVOKED
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.duckduckgo.mobile.android.vpn.ui.OpenVpnBreakageCategoryWithBrokenApp
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.alerts.reconnect.NetPReconnectNotifications
import com.duckduckgo.networkprotection.impl.di.NetpBreakageCategories
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.None
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowAlwaysOnLockdownEnabled
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowReconnecting
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowReconnectingFailed
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowRevoked
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.CheckVPNPermission
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.OpenVPNSettings
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.RequestVPNPermission
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowIssueReportingPage
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connecting
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Disconnected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Unknown
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository.ReconnectStatus
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository.ReconnectStatus.NotReconnecting
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository.ReconnectStatus.Reconnecting
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository.ReconnectStatus.ReconnectingFailed
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("NoLifecycleObserver") // does not subscribe to app lifecycle
@ContributesViewModel(ActivityScope::class)
class NetworkProtectionManagementViewModel @Inject constructor(
    private val vpnStateMonitor: VpnStateMonitor,
    private val featuresRegistry: VpnFeaturesRegistry,
    private val networkProtectionRepository: NetworkProtectionRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val reconnectNotifications: NetPReconnectNotifications,
    private val externalVpnDetector: ExternalVpnDetector,
    private val networkProtectionPixels: NetworkProtectionPixels,
    @NetpBreakageCategories private val netpBreakageCategories: List<AppBreakageCategory>,
) : ViewModel(), DefaultLifecycleObserver {

    private var reconnectStateFlow = MutableStateFlow(networkProtectionRepository.reconnectStatus)
    private val refreshVpnRunningState = MutableStateFlow(System.currentTimeMillis())
    private val connectionDetailsFlow = MutableStateFlow<ConnectionDetails?>(null)
    private val command = Channel<Command>(1, DROP_OLDEST)

    private var isTimerTickRunning: Boolean = false
    private var timerTickJob = ConflatedJob()
    private var lastVpnRequestTime = -1L

    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    internal fun viewState(): Flow<ViewState> {
        return combine(connectionDetailsFlow, getRunningState(), reconnectStateFlow) { connectionDetails, vpnState, reconnectState ->
            var connectionDetailsToEmit = connectionDetails

            if (vpnState.state == ENABLED && !isTimerTickRunning) {
                startElapsedTimeTimer()
            } else if (vpnState.state == DISABLED || vpnState.state == ENABLING) {
                stopElapsedTimeTimer()
                connectionDetailsToEmit = null
            } else if (reconnectState == Reconnecting) {
                connectionDetailsToEmit = null
            }

            return@combine ViewState(
                connectionState = vpnState.toConnectionState(reconnectState),
                connectionDetails = connectionDetailsToEmit,
                alertState = getAlertState(vpnState.state, reconnectState, vpnState.stopReason, vpnState.alwaysOnState),
            )
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        viewModelScope.launch(dispatcherProvider.io()) {
            // This is a one-shot check run 500ms after the screen is shown
            delay(500)
            getRunningState().firstOrNull()?.alwaysOnState?.let {
                handleAlwaysOnInitialState(it)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        stopElapsedTimeTimer()
    }

    @VisibleForTesting
    internal fun getAlertState(
        vpnRunningState: VpnRunningState,
        reconnectState: ReconnectStatus,
        vpnStopReason: VpnStopReason?,
        vpnAlwaysOnState: AlwaysOnState,
    ): AlertState {
        return if (vpnRunningState == DISABLED && (reconnectState == Reconnecting || reconnectState == ReconnectingFailed)) {
            ShowReconnectingFailed
        } else if (reconnectState == Reconnecting) {
            ShowReconnecting
        } else if (vpnRunningState == DISABLED && (vpnStopReason == REVOKED || vpnStopReason == ERROR)) {
            ShowRevoked
        } else if (vpnRunningState == ENABLED && vpnAlwaysOnState.isAlwaysOnLockedDown()) {
            ShowAlwaysOnLockdownEnabled
        } else {
            None
        }
    }

    private fun getRunningState(): Flow<VpnState> = vpnStateMonitor
        .getStateFlow(NetPVpnFeature.NETP_VPN)
        .combine(refreshVpnRunningState.asStateFlow()) { state, _ -> state }

    private fun VpnState.toConnectionState(reconnectState: ReconnectStatus): ConnectionState =
        if (this.state != DISABLED && reconnectState == Reconnecting) {
            Connecting
        } else {
            when (this.state) {
                ENABLING -> Connecting
                ENABLED -> Connected
                DISABLED -> Disconnected
                else -> Unknown
            }
        }

    private fun loadConnectionDetails() {
        networkProtectionRepository.serverDetails.run {
            this?.let { serverDetails ->
                connectionDetailsFlow.value = if (connectionDetailsFlow.value == null) {
                    ConnectionDetails(
                        location = serverDetails.location ?: "Los Angeles, United States",
                        ipAddress = serverDetails.ipAddress,
                    )
                } else {
                    connectionDetailsFlow.value!!.copy(
                        location = serverDetails.location ?: "Los Angeles, United States",
                        ipAddress = serverDetails.ipAddress,
                    )
                }
            }
        }
    }

    private fun startElapsedTimeTimer() {
        if (!isTimerTickRunning) {
            isTimerTickRunning = true
            loadConnectionDetails()
            timerTickJob += viewModelScope.launch(dispatcherProvider.default()) {
                var enabledTime = networkProtectionRepository.enabledTimeInMillis
                while (isTimerTickRunning) {
                    if (enabledTime == -1L) {
                        // We can't do anything with  a -1 enabledTime so we try to refetch it.
                        enabledTime = networkProtectionRepository.enabledTimeInMillis
                    } else {
                        val dataVolume = networkProtectionRepository.dataVolume
                        connectionDetailsFlow.value = if (connectionDetailsFlow.value == null) {
                            ConnectionDetails(
                                elapsedConnectedTime = getElapsedTimeString(enabledTime),
                                receivedBytes = dataVolume?.received ?: 0L,
                                transmittedBytes = dataVolume?.transmitted ?: 0L,
                            )
                        } else {
                            connectionDetailsFlow.value!!.copy(
                                elapsedConnectedTime = getElapsedTimeString(enabledTime),
                                receivedBytes = dataVolume?.received ?: 0L,
                                transmittedBytes = dataVolume?.transmitted ?: 0L,
                            )
                        }
                    }
                    reconnectStateFlow.value = networkProtectionRepository.reconnectStatus
                    delay(500)
                }
            }
        }
    }

    private fun getElapsedTimeString(enabledTime: Long): String {
        val elapsedTime = System.currentTimeMillis() - enabledTime
        return elapsedTime.toDisplayableTimerText()
    }

    private fun stopElapsedTimeTimer() {
        if (isTimerTickRunning) {
            isTimerTickRunning = false
            timerTickJob.cancel()
        }
    }

    fun onRequiredPermissionNotGranted(
        vpnIntent: Intent,
        lastVpnRequestTimeInMillis: Long,
    ) {
        lastVpnRequestTime = lastVpnRequestTimeInMillis
        sendCommand(RequestVPNPermission(vpnIntent))
    }

    fun onNetpToggleClicked(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (enabled) {
                if (externalVpnDetector.isExternalVpnDetected()) {
                    networkProtectionPixels.reportVpnConflictDialogShown()
                    sendCommand(Command.ShowVpnConflictDialog)
                } else {
                    sendCommand(CheckVPNPermission)
                }
            } else {
                onStopVpn()
            }
        }
    }

    fun onVPNPermissionRejected(rejectTimeInMillis: Long) {
        sendCommand(Command.ResetToggle)
        if (rejectTimeInMillis - lastVpnRequestTime < 500) {
            networkProtectionPixels.reportAlwaysOnConflictDialogShown()
            sendCommand(Command.ShowVpnAlwaysOnConflictDialog)
        }
        lastVpnRequestTime = -1L
    }

    fun onStartVpn() {
        viewModelScope.launch(dispatcherProvider.io()) {
            featuresRegistry.registerFeature(NetPVpnFeature.NETP_VPN)
            // TODO find a better place to reset values when manually starting or stopping NetP.
            networkProtectionRepository.reconnectStatus = NotReconnecting
            networkProtectionRepository.enabledTimeInMillis = -1L
            networkProtectionRepository.dataVolume = null
            reconnectNotifications.clearNotifications()
            forceUpdateRunningState()
            tryShowAlwaysOnPromotion()
        }
    }

    fun onReportIssuesClicked() {
        sendCommand(
            ShowIssueReportingPage(
                OpenVpnBreakageCategoryWithBrokenApp(
                    launchFrom = "netp",
                    appName = "",
                    appPackageId = "",
                    breakageCategories = netpBreakageCategories,
                ),
            ),
        )
    }

    private fun tryShowAlwaysOnPromotion() {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (shouldShowAlwaysOnPromotion()) {
                networkProtectionPixels.reportAlwaysOnPromotionDialogShown()
                sendCommand(Command.ShowAlwaysOnPromotionDialog)
            }
        }
    }

    private fun handleAlwaysOnInitialState(alwaysOnState: VpnStateMonitor.AlwaysOnState) {
        if (alwaysOnState.enabled && alwaysOnState.lockedDown) {
            networkProtectionPixels.reportAlwaysOnLockdownDialogShown()
            sendCommand(Command.ShowAlwaysOnLockdownDialog)
        }
    }

    fun onOpenSettingsFromAlwaysOnPromotionClicked() {
        networkProtectionPixels.reportOpenSettingsFromAlwaysOnPromotion()
        sendCommand(OpenVPNSettings)
    }

    fun onOpenSettingsFromAlwaysOnLockdownClicked() {
        networkProtectionPixels.reportOpenSettingsFromAlwaysOnLockdown()
        sendCommand(OpenVPNSettings)
    }

    private fun onStopVpn() {
        viewModelScope.launch(dispatcherProvider.io()) {
            featuresRegistry.unregisterFeature(NetPVpnFeature.NETP_VPN)
            reconnectNotifications.clearNotifications()
            forceUpdateRunningState()
        }
    }

    private suspend fun shouldShowAlwaysOnPromotion(): Boolean {
        return !vpnStateMonitor.isAlwaysOnEnabled() && vpnStateMonitor.vpnLastDisabledByAndroid()
    }

    private suspend fun forceUpdateRunningState() = withContext(dispatcherProvider.io()) {
        // If the VPN is not started due to any issue, the getRunningState() won't be updated and the toggle is kept (wrongly) in ON state
        // Check after 1 second to ensure this doesn't happen
        delay(TimeUnit.SECONDS.toMillis(1))
        refreshVpnRunningState.emit(System.currentTimeMillis())
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch(dispatcherProvider.io()) {
            command.send(newCommand)
        }
    }

    sealed class Command {
        object CheckVPNPermission : Command()
        data class RequestVPNPermission(val vpnIntent: Intent) : Command()
        object ShowVpnAlwaysOnConflictDialog : Command()
        object ShowVpnConflictDialog : Command()
        object ResetToggle : Command()
        object ShowAlwaysOnPromotionDialog : Command()
        object ShowAlwaysOnLockdownDialog : Command()
        object OpenVPNSettings : Command()
        data class ShowIssueReportingPage(val params: OpenVpnBreakageCategoryWithBrokenApp) : Command()
    }

    data class ViewState(
        val connectionState: ConnectionState = Disconnected,
        val connectionDetails: ConnectionDetails? = null,
        val alertState: AlertState = None,
    )

    data class ConnectionDetails(
        val location: String? = null,
        val ipAddress: String? = null,
        val elapsedConnectedTime: String? = null,
        val receivedBytes: Long = 0L,
        val transmittedBytes: Long = 0L,
    )

    enum class ConnectionState {
        Connecting,
        Connected,
        Disconnected,
        Unknown,
    }

    enum class AlertState {
        ShowReconnecting,
        ShowReconnectingFailed,
        ShowRevoked,
        ShowAlwaysOnLockdownEnabled,
        None,
    }
}
