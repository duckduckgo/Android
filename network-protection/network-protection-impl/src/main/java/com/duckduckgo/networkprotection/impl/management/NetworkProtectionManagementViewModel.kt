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

import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLING
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.CheckVPNPermission
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.Command.RequestVPNPermission
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Connecting
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Disconnected
import com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementViewModel.ConnectionState.Unknown
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class NetworkProtectionManagementViewModel @Inject constructor(
    private val vpnStateMonitor: VpnStateMonitor,
    private val featuresRegistry: VpnFeaturesRegistry,
    private val networkProtectionRepository: NetworkProtectionRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    private val refreshVpnRunningState = MutableStateFlow(System.currentTimeMillis())
    private val connectionDetailsFlow = MutableStateFlow<ConnectionDetails?>(null)
    private val command = Channel<Command>(1, DROP_OLDEST)

    private var isTimerTickRunning: Boolean = false
    private var timerTickJob = ConflatedJob()

    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    internal fun viewState(): Flow<ViewState> {
        return connectionDetailsFlow.combine(getRunningState()) { connectionDetails, vpnState ->
            var connectionDetailsToEmit = connectionDetails

            if (vpnState.state == ENABLED && !isTimerTickRunning) {
                startElapsedTimeTimer()
            } else if (vpnState.state == DISABLED) {
                stopElapsedTimeTimer()
                connectionDetailsToEmit = null
            }

            return@combine ViewState(
                connectionState = vpnState.toConnectionState(),
                connectionDetails = connectionDetailsToEmit,
            )
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        stopElapsedTimeTimer()
    }

    private fun getRunningState(): Flow<VpnState> = vpnStateMonitor
        .getStateFlow(NetPVpnFeature.NETP_VPN)
        .combine(refreshVpnRunningState.asStateFlow()) { state, _ -> state }

    private fun VpnState.toConnectionState(): ConnectionState = when (this.state) {
        ENABLING -> Connecting
        ENABLED -> Connected
        DISABLED -> Disconnected
        else -> Unknown
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
                        connectionDetailsFlow.value = if (connectionDetailsFlow.value == null) {
                            ConnectionDetails(
                                elapsedConnectedTime = getElapsedTimeString(enabledTime),
                            )
                        } else {
                            connectionDetailsFlow.value!!.copy(
                                elapsedConnectedTime = getElapsedTimeString(enabledTime),
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

    fun onRequiredPermissionNotGranted(vpnIntent: Intent) {
        sendCommand(RequestVPNPermission(vpnIntent))
    }

    fun onNetpToggleClicked(enabled: Boolean) {
        if (enabled) {
            sendCommand(CheckVPNPermission)
        } else {
            onStopVpn()
        }
    }

    fun onStartVpn() {
        featuresRegistry.registerFeature(NetPVpnFeature.NETP_VPN)
        forceUpdateRunningState()
    }

    private fun onStopVpn() {
        featuresRegistry.unregisterFeature(NetPVpnFeature.NETP_VPN)
        forceUpdateRunningState()
    }

    private fun forceUpdateRunningState() {
        // If the VPN is not started due to any issue, the getRunningState() won't be updated and the toggle is kept (wrongly) in ON state
        // Check after 1 second to ensure this doesn't happen
        viewModelScope.launch {
            delay(TimeUnit.SECONDS.toMillis(1))
            refreshVpnRunningState.emit(System.currentTimeMillis())
        }
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }

    sealed class Command {
        object CheckVPNPermission : Command()
        data class RequestVPNPermission(val vpnIntent: Intent) : Command()
    }

    data class ViewState(
        val connectionState: ConnectionState = Disconnected,
        val connectionDetails: ConnectionDetails? = null,
    )

    data class ConnectionDetails(
        val location: String? = null,
        val ipAddress: String? = null,
        val elapsedConnectedTime: String? = null,
    )

    enum class ConnectionState {
        Connecting,
        Connected,
        Disconnected,
        Unknown,
    }
}
