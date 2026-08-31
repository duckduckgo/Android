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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

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
                        // The VPN is on: surface the server the traffic is now routed through.
                        val server = wgTunnelConfig.getWgConfig()?.asServerDetails()
                        viewState.update {
                            it.copy(
                                vpnEnabled = true,
                                activating = false,
                                activationError = null,
                                newIpAddress = server?.ipAddress,
                                newLocation = formatVpnServerLocation(server?.location),
                            )
                        }
                    }
                    CONNECTING -> viewState.update { it.copy(vpnEnabled = false, activating = true, activationError = null) }
                    DISCONNECTED -> viewState.update {
                        // Falling back to disconnected while activation was in progress means the VPN failed to start.
                        it.copy(
                            vpnEnabled = false,
                            activating = false,
                            activationError = if (it.activating) ActivationError.FAILED else it.activationError,
                        )
                    }
                }
            }
            .flowOn(dispatcherProvider.io())
            .launchIn(viewModelScope)
    }

    /** The VPN permission was granted (or already present): clear any error, show activation in progress, start the VPN. */
    fun onVpnPermissionGranted() {
        viewState.update { it.copy(activationError = null, activating = true) }
        networkProtectionState.start()
    }

    /** The user declined the system VPN configuration dialog: surface the permission activation error state. */
    fun onVpnPermissionDenied() {
        viewState.update { it.copy(activationError = ActivationError.PERMISSION_DENIED) }
    }

    /**
     * The primary button drives the whole step: it turns the VPN on, then opens the info page, then finishes
     * the step. Which of those it does depends on the current state.
     */
    fun onPrimaryCtaClicked() {
        val state = viewState.value
        when {
            state.activating -> {} // ignore taps while the VPN is being activated
            state.showingInfo -> controller.onStepFinished(VPN_STEP_ID, COMPLETED)
            state.vpnEnabled == true -> viewState.update { it.copy(showingInfo = true) }
            else -> viewModelScope.launch { _commands.send(Command.RequestVpnPermission) }
        }
    }

    fun onSkipClicked() {
        controller.onStepFinished(VPN_STEP_ID, SKIPPED)
    }

    data class ViewState(
        // null until the VPN connection state is first observed, so the UI does not flip states prematurely.
        val vpnEnabled: Boolean? = null,
        val activating: Boolean = false,
        val activationError: ActivationError? = null,
        val ipAddress: String? = null,
        val location: String? = null,
        val newIpAddress: String? = null,
        val newLocation: String? = null,
        // Once the info page is open it takes over the screen and the connection state no longer drives it.
        val showingInfo: Boolean = false,
    )

    sealed interface Command {
        /** Only the view can call [android.net.VpnService.prepare], so asking for the permission is delegated back to it. */
        data object RequestVpnPermission : Command
    }

    /**
     * Why VPN activation failed. [PERMISSION_DENIED] shows the "allow the configuration" guidance; [FAILED]
     * (the VPN could not be started for any other reason) shows the same error screen without that guidance.
     */
    enum class ActivationError { PERMISSION_DENIED, FAILED }

    companion object {
        private const val UNKNOWN_IP = "XXX.XXX.XX.XXX"
        private const val UNKNOWN_LOCATION = "XX, XX"
    }
}

/**
 * Formats a WireGuard peer location ("City, CC") into the "🇨🇨 City, Country" label used on the screen,
 * or null when the location is missing or not in the expected shape.
 */
internal fun formatVpnServerLocation(location: String?): String? {
    val parts = location?.split(",") ?: return null
    val city = parts.getOrNull(0)?.trim().orEmpty()
    val countryCode = parts.getOrNull(1)?.trim().orEmpty()
    if (city.isEmpty() || countryCode.isEmpty()) return null
    return "${getEmojiForCountryCode(countryCode)} $city, ${getDisplayableCountry(countryCode)}"
}
