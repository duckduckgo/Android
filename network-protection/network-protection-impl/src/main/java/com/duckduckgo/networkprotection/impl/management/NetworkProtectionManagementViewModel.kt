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
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.Toggle.State
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
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.VpnRemoteFeatures
import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludePrompt
import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludePrompt.Trigger.NEW_INCOMPATIBLE_APP_FOUND
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.asServerDetails
import com.duckduckgo.networkprotection.impl.di.NetpBreakageCategories
import com.duckduckgo.networkprotection.impl.exclusion.NetPExclusionListRepository
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.None
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowAlwaysOnLockdownEnabled
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.AlertState.ShowRevoked
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.CheckVPNPermission
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.OpenVPNSettings
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.RequestVPNPermission
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowAutoExcludeDialog
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowIssueReportingPage
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.ShowUnifiedFeedback
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connecting
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Disconnected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Unknown
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.impl.settings.NetpVpnSettingsDataStore
import com.duckduckgo.networkprotection.impl.settings.geoswitching.getDisplayableCountry
import com.duckduckgo.networkprotection.impl.settings.geoswitching.getEmojiForCountryCode
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.volume.NetpDataVolumeStore
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository.UserPreferredLocation
import com.duckduckgo.networkprotection.store.db.VpnIncompatibleApp
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.VPN_MANAGEMENT
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // does not subscribe to app lifecycle
@ContributesViewModel(ActivityScope::class)
class NetworkProtectionManagementViewModel @Inject constructor(
    private val vpnStateMonitor: VpnStateMonitor,
    private val networkProtectionRepository: NetworkProtectionRepository,
    private val wgTunnelConfig: WgTunnelConfig,
    private val dispatcherProvider: DispatcherProvider,
    private val externalVpnDetector: ExternalVpnDetector,
    private val networkProtectionPixels: NetworkProtectionPixels,
    @NetpBreakageCategories private val netpBreakageCategories: List<AppBreakageCategory>,
    private val networkProtectionState: NetworkProtectionState,
    private val netPGeoswitchingRepository: NetPGeoswitchingRepository,
    private val netpDataVolumeStore: NetpDataVolumeStore,
    private val netPExclusionListRepository: NetPExclusionListRepository,
    private val netpVpnSettingsDataStore: NetpVpnSettingsDataStore,
    private val privacyProUnifiedFeedback: PrivacyProUnifiedFeedback,
    private val vpnRemoteFeatures: VpnRemoteFeatures,
    private val localConfig: NetPSettingsLocalConfig,
    private val autoExcludePrompt: AutoExcludePrompt,
) : ViewModel(), DefaultLifecycleObserver {

    private val refreshVpnRunningState = MutableStateFlow(System.currentTimeMillis())
    private val connectionDetailsFlow = MutableStateFlow<ConnectionDetails?>(null)
    private val command = Channel<Command>(1, DROP_OLDEST)

    private var isTimerTickRunning: Boolean = false
    private var timerTickJob = ConflatedJob()
    private var lastVpnRequestTime = -1L

    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    internal fun viewState(): Flow<ViewState> {
        return combine(connectionDetailsFlow, getRunningState()) { connectionDetails, vpnState ->
            val preferredLocation = netPGeoswitchingRepository.getUserPreferredLocation()
            var connectionDetailsToEmit = connectionDetails
            var locationState: LocationState? = connectionDetails?.toLocationState(preferredLocation.countryCode)

            if (vpnState.state == ENABLED && !isTimerTickRunning) {
                startElapsedTimeTimer()
            } else if (vpnState.state is DISABLED || vpnState.state == ENABLING) {
                stopElapsedTimeTimer()
                connectionDetailsToEmit = null
                locationState = preferredLocation.toLocationState()
            }

            return@combine ViewState(
                connectionState = vpnState.toConnectionState(),
                connectionDetails = connectionDetailsToEmit,
                alertState = getAlertState(vpnState.state, vpnState.stopReason, vpnState.alwaysOnState),
                locationState = locationState,
                excludedAppsCount = netPExclusionListRepository.getExcludedAppPackages().size,
            )
        }
    }

    private fun UserPreferredLocation.toLocationState(): LocationState {
        return LocationState(
            icon = countryCode?.run {
                getEmojiForCountryCode(this)
            },
            isCustom = countryCode != null,
            location = if (!cityName.isNullOrEmpty()) {
                "${cityName!!}, ${getDisplayableCountry(countryCode!!)}"
            } else {
                countryCode?.let {
                    getDisplayableCountry(it)
                }
            },
        )
    }

    private fun ConnectionDetails.toLocationState(preferredCountry: String?): LocationState {
        // split can throw index out of bounds
        val city = runCatching { location?.split(",")?.get(0)?.trim() }.getOrNull()
        val countryCode = runCatching { location?.split(",")?.get(1)?.trim() }.getOrNull()
        val location = if (city != null && countryCode != null) {
            "$city, ${getDisplayableCountry(countryCode)}"
        } else {
            null
        }
        return LocationState(
            icon = countryCode?.run {
                getEmojiForCountryCode(this)
            },
            isCustom = preferredCountry != null,
            location = location,
        )
    }

    override fun onCreate(owner: LifecycleOwner) {
        networkProtectionPixels.reportVpnScreenShown()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        viewModelScope.launch(dispatcherProvider.io()) {
            // This is a one-shot check run 500ms after the screen is shown
            delay(500)
            getRunningState().firstOrNull()?.alwaysOnState?.let {
                handleAlwaysOnInitialState(it)
            }
            tryShowAutoExcludePrompt()
        }
    }

    private suspend fun tryShowAutoExcludePrompt() {
        if (networkProtectionState.isRunning() &&
            !localConfig.autoExcludeBrokenApps().isEnabled()
        ) {
            autoExcludePrompt.getAppsForPrompt(NEW_INCOMPATIBLE_APP_FOUND).also {
                if (it.isNotEmpty()) {
                    sendCommand(ShowAutoExcludeDialog(it))
                }
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
        vpnStopReason: VpnStopReason?,
        vpnAlwaysOnState: AlwaysOnState,
    ): AlertState {
        return if (vpnRunningState == DISABLED && (vpnStopReason == REVOKED || vpnStopReason == ERROR)) {
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

    private fun VpnState.toConnectionState(): ConnectionState {
        return when (this.state) {
            ENABLING -> Connecting
            ENABLED -> Connected
            is DISABLED -> Disconnected
            else -> Unknown
        }
    }

    private suspend fun loadConnectionDetails() {
        wgTunnelConfig.getWgConfig()?.asServerDetails()?.let { serverDetails ->
            connectionDetailsFlow.value = if (connectionDetailsFlow.value == null) {
                ConnectionDetails(
                    location = serverDetails.location,
                    ipAddress = serverDetails.ipAddress,
                    customDns = netpVpnSettingsDataStore.customDns,
                )
            } else {
                connectionDetailsFlow.value!!.copy(
                    location = serverDetails.location,
                    ipAddress = serverDetails.ipAddress,
                    customDns = netpVpnSettingsDataStore.customDns,
                )
            }
        }
    }

    private suspend fun startElapsedTimeTimer() {
        if (!isTimerTickRunning) {
            isTimerTickRunning = true
            loadConnectionDetails()
            timerTickJob += viewModelScope.launch(dispatcherProvider.io()) {
                var enabledTime = networkProtectionRepository.enabledTimeInMillis
                while (isTimerTickRunning) {
                    if (enabledTime == -1L) {
                        // We can't do anything with  a -1 enabledTime so we try to refetch it.
                        enabledTime = networkProtectionRepository.enabledTimeInMillis
                    } else {
                        val dataVolume = netpDataVolumeStore.dataVolume
                        connectionDetailsFlow.value = if (connectionDetailsFlow.value == null) {
                            ConnectionDetails(
                                elapsedConnectedTime = getElapsedTimeString(enabledTime),
                                transmittedData = dataVolume.transmittedBytes,
                                receivedData = dataVolume.receivedBytes,
                                customDns = netpVpnSettingsDataStore.customDns,
                            )
                        } else {
                            connectionDetailsFlow.value!!.copy(
                                elapsedConnectedTime = getElapsedTimeString(enabledTime),
                                transmittedData = dataVolume.transmittedBytes,
                                receivedData = dataVolume.receivedBytes,
                                customDns = netpVpnSettingsDataStore.customDns,
                            )
                        }
                    }
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
                if (vpnRemoteFeatures.showExcludeAppPrompt().isEnabled() && !localConfig.permanentRemoveExcludeAppPrompt().isEnabled()) {
                    networkProtectionPixels.reportExcludePromptShown()
                    sendCommand(Command.ShowExcludeAppPrompt)
                } else {
                    onStopVpn()
                }
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
            networkProtectionState.start()
            networkProtectionRepository.enabledTimeInMillis = -1L
            forceUpdateRunningState()
            tryShowAlwaysOnPromotion()
        }
    }

    fun onReportIssuesClicked() {
        viewModelScope.launch {
            if (privacyProUnifiedFeedback.shouldUseUnifiedFeedback(source = VPN_MANAGEMENT)) {
                sendCommand(ShowUnifiedFeedback)
            } else {
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
        }
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
            networkProtectionState.clearVPNConfigurationAndStop()
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

    fun onConfirmDisableVpn() {
        networkProtectionPixels.reportExcludePromptDisableVpnClicked()
        onStopVpn()
    }

    @SuppressLint("DenyListedApi")
    fun onDontShowExcludeAppPromptAgain() {
        networkProtectionPixels.reportExcludePromptDontAskAgainClicked()
        localConfig.permanentRemoveExcludeAppPrompt().setRawStoredState(State(enable = true))
    }

    fun onExcludeAppSelected() {
        networkProtectionPixels.reportExcludePromptExcludeAppClicked()
    }

    sealed class Command {
        data object CheckVPNPermission : Command()
        data class RequestVPNPermission(val vpnIntent: Intent) : Command()
        data object ShowVpnAlwaysOnConflictDialog : Command()
        data object ShowVpnConflictDialog : Command()
        data object ResetToggle : Command()
        data object ShowAlwaysOnPromotionDialog : Command()
        data object ShowAlwaysOnLockdownDialog : Command()
        data object OpenVPNSettings : Command()
        data class ShowIssueReportingPage(val params: OpenVpnBreakageCategoryWithBrokenApp) : Command()
        data object ShowUnifiedFeedback : Command()
        data object ShowExcludeAppPrompt : Command()
        data class ShowAutoExcludeDialog(val apps: List<VpnIncompatibleApp>) : Command()
    }

    data class ViewState(
        val connectionState: ConnectionState = Disconnected,
        val connectionDetails: ConnectionDetails? = null,
        val alertState: AlertState = None,
        val locationState: LocationState? = null,
        val excludedAppsCount: Int = 0,
    )

    data class LocationState(
        val location: String?,
        val icon: String?,
        val isCustom: Boolean,
    )

    data class ConnectionDetails(
        val location: String? = null,
        val ipAddress: String? = null,
        val elapsedConnectedTime: String? = null,
        val transmittedData: Long = 0L,
        val receivedData: Long = 0L,
        val customDns: String? = null,
    )

    enum class ConnectionState {
        Connecting,
        Connected,
        Disconnected,
        Unknown,
    }

    enum class AlertState {
        ShowRevoked,
        ShowAlwaysOnLockdownEnabled,
        None,
    }
}
