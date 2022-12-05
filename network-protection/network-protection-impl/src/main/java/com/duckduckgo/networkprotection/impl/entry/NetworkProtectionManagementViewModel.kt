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

package com.duckduckgo.networkprotection.impl.entry

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.entry.NetworkProtectionManagementViewModel.Command.CheckVPNPermission
import com.duckduckgo.networkprotection.impl.entry.NetworkProtectionManagementViewModel.Command.RequestVPNPermission
import com.duckduckgo.networkprotection.impl.entry.NetworkProtectionManagementViewModel.ConnectionState.Connected
import com.duckduckgo.networkprotection.impl.entry.NetworkProtectionManagementViewModel.ConnectionState.Disconnected
import java.util.concurrent.TimeUnit.SECONDS
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesViewModel(ActivityScope::class)
class NetworkProtectionManagementViewModel @Inject constructor(
    private val vpnStateMonitor: VpnStateMonitor,
    private val featuresRegistry: VpnFeaturesRegistry,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val refreshVpnRunningState = MutableStateFlow(System.currentTimeMillis())
    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, DROP_OLDEST)

    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    internal fun viewState(): Flow<ViewState> {
        return viewState.combine(getRunningState()) { viewState, vpnState ->
            viewState.copy(
                connectionState = if (vpnState.state == VpnRunningState.ENABLED) Connected else Disconnected,
            )
        }
    }

    internal suspend fun loadData() = withContext(dispatcherProvider.io()) {
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    connectionState = if (featuresRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)) Connected else Disconnected,
                ),
            )
        }
    }

    private fun getRunningState(): Flow<VpnState> = vpnStateMonitor
        .getStateFlow(NetPVpnFeature.NETP_VPN)
        .combine(refreshVpnRunningState.asStateFlow()) { state, _ -> state }

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

    fun onStopVpn() {
        featuresRegistry.unregisterFeature(NetPVpnFeature.NETP_VPN)
        forceUpdateRunningState()
    }

    private fun forceUpdateRunningState() {
        // If the VPN is not started due to any issue, the getRunningState() won't be updated and the toggle is kept (wrongly) in ON state
        // Check after 1 second to ensure this doesn't happen
        viewModelScope.launch {
            delay(SECONDS.toMillis(1))
            refreshVpnRunningState.emit(System.currentTimeMillis())
        }
    }

    private fun currentViewState(): ViewState {
        return viewState.value
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
    )

    enum class ConnectionState {
        Connected,
        Disconnected,
    }
}
