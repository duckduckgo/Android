/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.subscription.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.settings.geoswitching.getDisplayableCountry
import com.duckduckgo.networkprotection.impl.settings.geoswitching.getEmojiForCountryCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class SubscriptionOnboardingVpnViewModel @Inject constructor(
    private val connectionService: SubscriptionOnboardingConnectionService,
    private val networkProtectionState: NetworkProtectionState,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.asStateFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io()) {
            runCatching { connectionService.getConnectionInfo() }
                .onSuccess { info ->
                    viewState.update {
                        it.copy(
                            ipAddress = info.ip,
                            location = "${getEmojiForCountryCode(info.country)} ${info.city}, ${getDisplayableCountry(info.country)}",
                        )
                    }
                }
                .onFailure {
                    logcat(WARN) { "Unable to load connection info: ${it.asLog()}" }
                    viewState.update { state ->
                        state.copy(ipAddress = UNKNOWN_IP, location = UNKNOWN_LOCATION)
                    }
                }
        }

        networkProtectionState.getConnectionStateFlow()
            .onEach { connectionState ->
                val connected = connectionState.isConnected()
                viewState.update {
                    // Once the VPN is on, any earlier activation error is resolved.
                    it.copy(vpnEnabled = connected, activationError = if (connected) false else it.activationError)
                }
            }
            .flowOn(dispatcherProvider.io())
            .launchIn(viewModelScope)
    }

    /** The VPN permission was granted (or already present): clear any error and start the VPN. */
    fun onVpnPermissionGranted() {
        viewState.update { it.copy(activationError = false) }
        networkProtectionState.start()
    }

    /** The user declined the system VPN configuration dialog: surface the activation error state. */
    fun onVpnPermissionDenied() {
        viewState.update { it.copy(activationError = true) }
    }

    data class ViewState(
        // null until the VPN connection state is first observed, so the UI does not flip states prematurely.
        val vpnEnabled: Boolean? = null,
        val activationError: Boolean = false,
        val ipAddress: String? = null,
        val location: String? = null,
    )

    companion object {
        private const val UNKNOWN_IP = "XXX.XXX.XX.XXX"
        private const val UNKNOWN_LOCATION = "XX, XX"
    }
}
