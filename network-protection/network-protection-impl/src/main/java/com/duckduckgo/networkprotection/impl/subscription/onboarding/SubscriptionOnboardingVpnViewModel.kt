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
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTED
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTING
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.DISCONNECTED
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.asServerDetails
import com.duckduckgo.networkprotection.impl.settings.geoswitching.getDisplayableCountry
import com.duckduckgo.networkprotection.impl.settings.geoswitching.getEmojiForCountryCode
import com.duckduckgo.networkprotection.impl.subscription.onboarding.SubscriptionOnboardingVpnStepPlugin.Companion.VPN_STEP_ID
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.SKIPPED
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@ContributesViewModel(FragmentScope::class)
class SubscriptionOnboardingVpnViewModel @Inject constructor(
    private val controller: SubscriptionOnboardingController,
    private val connectionService: SubscriptionOnboardingConnectionService,
    private val networkProtectionState: NetworkProtectionState,
    private val wgTunnelConfig: WgTunnelConfig,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.asStateFlow()

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private val activationTimeoutJob = ConflatedJob()

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
                when (connectionState) {
                    CONNECTED -> {
                        val server = wgTunnelConfig.getWgConfig()?.asServerDetails()
                        viewState.update {
                            it.copy(
                                vpnEnabled = true,
                                activating = false,
                                vpnActivationError = null,
                                newIpAddress = server?.ipAddress,
                                newLocation = formatVpnServerLocation(server?.location),
                            )
                        }
                    }
                    CONNECTING -> viewState.update {
                        it.copy(
                            vpnEnabled = false,
                            activating = true,
                            vpnActivationError = null,
                        )
                    }
                    DISCONNECTED -> viewState.update {
                        // Falling back to disconnected while activation was in progress means the VPN failed to start.
                        it.copy(
                            vpnEnabled = false,
                            activating = false,
                            vpnActivationError = if (it.activating) VPNActivationError.CONNECTION_FAILED else it.vpnActivationError,
                        )
                    }
                }
            }
            .flowOn(dispatcherProvider.io())
            .launchIn(viewModelScope)
    }

    fun onVpnPermissionGranted() {
        viewState.update { it.copy(vpnActivationError = null, activating = true) }
        networkProtectionState.start()
        activationTimeoutJob += viewModelScope.launch(dispatcherProvider.io()) {
            val connected = withTimeoutOrNull(ACTIVATION_TIMEOUT_MILLIS.milliseconds) {
                networkProtectionState.getConnectionStateFlow().firstOrNull { it == CONNECTED }
            }
            if (connected == null) {
                viewState.update {
                    if (it.activating) it.copy(activating = false, vpnActivationError = VPNActivationError.CONNECTION_FAILED) else it
                }
            }
        }
    }

    fun onVpnPermissionDenied() {
        viewState.update { it.copy(vpnActivationError = VPNActivationError.PERMISSION_DENIED) }
    }

    fun onPrimaryCtaClicked() {
        val state = viewState.value
        when {
            state.activating -> {} // ignore taps while the VPN is being activated
            state.showingVPNInfoBanners -> controller.onStepFinished(VPN_STEP_ID, COMPLETED)
            state.vpnEnabled == true -> viewState.update { it.copy(showingVPNInfoBanners = true) }
            else -> viewModelScope.launch { _commands.send(Command.RequestVpnPermission) }
        }
    }

    fun onSkipClicked() {
        controller.onStepFinished(VPN_STEP_ID, SKIPPED)
    }

    data class ViewState(
        val vpnEnabled: Boolean? = null,
        val activating: Boolean = false,
        val vpnActivationError: VPNActivationError? = null,
        val ipAddress: String? = null,
        val location: String? = null,
        val newIpAddress: String? = null,
        val newLocation: String? = null,
        val showingVPNInfoBanners: Boolean = false,
    )

    sealed interface Command {
        data object RequestVpnPermission : Command
    }

    enum class VPNActivationError {
        PERMISSION_DENIED,
        CONNECTION_FAILED,
    }

    companion object {
        private const val UNKNOWN_IP = "XXX.XXX.XX.XXX"
        private const val UNKNOWN_LOCATION = "XX, XX"
        private const val ACTIVATION_TIMEOUT_MILLIS = 15_000L
    }
}

internal fun formatVpnServerLocation(location: String?): String? {
    val parts = location?.split(",") ?: return null
    val city = parts.getOrNull(0)?.trim().orEmpty()
    val countryCode = parts.getOrNull(1)?.trim().orEmpty()
    if (city.isEmpty() || countryCode.isEmpty()) return null
    return "${getEmojiForCountryCode(countryCode)} $city, ${getDisplayableCountry(countryCode)}"
}
