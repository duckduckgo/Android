/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.subscription.settings

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toBinaryString
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionAccessState
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_SETTINGS_PRESSED
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.Command.OpenNetPScreen
import com.duckduckgo.subscriptions.api.Product.NetP
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
class ProSettingNetPViewModel(
    private val networkProtectionState: NetworkProtectionState,
    private val networkProtectionAccessState: NetworkProtectionAccessState,
    private val subscriptions: Subscriptions,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(val netPEntryState: NetPEntryState = NetPEntryState.Hidden)

    sealed class Command {
        data class OpenNetPScreen(val params: ActivityParams) : Command()
    }

    sealed class NetPEntryState {

        data object Hidden : NetPEntryState()
        data class Enabled(val isActive: Boolean) : NetPEntryState()
        data object Disabled : NetPEntryState()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        combine(
            subscriptions.getEntitlementStatus().map { entitledProducts -> entitledProducts.contains(NetP) },
            networkProtectionState.getConnectionStateFlow(),
        ) { netpEntitlementStatus, connectionState ->

            val subscriptionStatus = subscriptions.getSubscriptionStatus()

            val netPEntryState = getNetpEntryState(netpEntitlementStatus, connectionState, subscriptionStatus)

            _viewState.update { it.copy(netPEntryState = netPEntryState) }
        }
            .flowOn(dispatcherProvider.main())
            .launchIn(viewModelScope)
    }

    private suspend fun getNetpEntryState(
        netpEntitlementStatus: Boolean,
        connectionState: ConnectionState,
        subscriptionStatus: SubscriptionStatus,
    ): NetPEntryState {
        return when (subscriptionStatus) {
            SubscriptionStatus.UNKNOWN -> {
                handleRevokedVPNState()
                NetPEntryState.Hidden
            }

            SubscriptionStatus.INACTIVE,
            SubscriptionStatus.EXPIRED,
            SubscriptionStatus.WAITING,
            -> {
                if (hasNetpProduct()) {
                    NetPEntryState.Disabled
                } else {
                    handleRevokedVPNState()
                    NetPEntryState.Hidden
                }
            }

            SubscriptionStatus.AUTO_RENEWABLE,
            SubscriptionStatus.NOT_AUTO_RENEWABLE,
            SubscriptionStatus.GRACE_PERIOD,
            -> {
                if (netpEntitlementStatus) {
                    NetPEntryState.Enabled(isActive = connectionState.isConnected())
                } else {
                    // ensure VPN is stopped in case entitlement is revoked
                    handleRevokedVPNState()
                    NetPEntryState.Hidden
                }
            }
        }
    }

    fun onNetPSettingClicked() {
        viewModelScope.launch {
            val screen = networkProtectionAccessState.getScreenForCurrentState()
            screen?.let {
                command.send(OpenNetPScreen(screen))
                val wasUsedBefore = networkProtectionState.isOnboarded()
                pixel.fire(NETP_SETTINGS_PRESSED, parameters = mapOf("was_used_before" to wasUsedBefore.toBinaryString()))
            } ?: logcat { "Get screen for current NetP state is null" }
        }
    }

    private suspend fun hasNetpProduct(): Boolean {
        val products = subscriptions.getAvailableProducts()
        return products.contains(NetP)
    }

    private suspend fun handleRevokedVPNState() {
        if (networkProtectionState.isEnabled()) {
            networkProtectionState.stop()
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val networkProtectionState: NetworkProtectionState,
        private val networkProtectionAccessState: NetworkProtectionAccessState,
        private val subscriptions: Subscriptions,
        private val dispatcherProvider: DispatcherProvider,
        private val pixel: Pixel,
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return with(modelClass) {
                when {
                    isAssignableFrom(ProSettingNetPViewModel::class.java) -> ProSettingNetPViewModel(
                        networkProtectionState,
                        networkProtectionAccessState,
                        subscriptions,
                        dispatcherProvider,
                        pixel,
                    )

                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            } as T
        }
    }
}
