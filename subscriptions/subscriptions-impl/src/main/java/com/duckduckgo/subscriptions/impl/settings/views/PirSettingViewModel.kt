/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.settings.views

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.pir.api.PirFeature
import com.duckduckgo.subscriptions.api.Product.PIR
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.Command.OpenPirDesktop
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.ViewState.PirState
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.ViewState.PirState.Enabled.Type
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.ViewState.PirState.Enabled.Type.DASHBOARD
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.ViewState.PirState.Enabled.Type.DESKTOP
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class PirSettingViewModel @Inject constructor(
    private val pixelSender: SubscriptionPixelSender,
    private val subscriptions: Subscriptions,
    private val pirFeature: PirFeature,
) : ViewModel(), DefaultLifecycleObserver {

    sealed class Command {
        data object OpenPirDesktop : Command()
        data object OpenPirDashboard : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    data class ViewState(val pirState: PirState = PirState.Hidden) {

        sealed class PirState {

            data object Hidden : PirState()
            data class Enabled(val type: Type) : PirState() {
                enum class Type {
                    DESKTOP,
                    DASHBOARD,
                }
            }

            data object Disabled : PirState()
        }
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    fun onPir(type: Type) {
        pixelSender.reportAppSettingsPirClick()

        val command = when (type) {
            DESKTOP -> OpenPirDesktop
            DASHBOARD -> Command.OpenPirDashboard
        }
        sendCommand(command)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        subscriptions.getEntitlementStatus().map { entitledProducts -> entitledProducts.contains(PIR) }
            .onEach { hasValidEntitlement ->

                val subscriptionStatus = subscriptions.getSubscriptionStatus()

                val pirState = getPirState(hasValidEntitlement, subscriptionStatus)

                _viewState.update { it.copy(pirState = pirState) }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun getPirState(
        hasValidEntitlement: Boolean,
        subscriptionStatus: SubscriptionStatus,
    ): PirState {
        return when (subscriptionStatus) {
            SubscriptionStatus.UNKNOWN -> PirState.Hidden

            SubscriptionStatus.INACTIVE,
            SubscriptionStatus.EXPIRED,
            SubscriptionStatus.WAITING,
            -> {
                if (isPirAvailable()) {
                    PirState.Disabled
                } else {
                    PirState.Hidden
                }
            }

            SubscriptionStatus.AUTO_RENEWABLE,
            SubscriptionStatus.NOT_AUTO_RENEWABLE,
            SubscriptionStatus.GRACE_PERIOD,
            -> {
                if (hasValidEntitlement) {
                    val type = if (pirFeature.isPirBetaEnabled()) {
                        DASHBOARD
                    } else {
                        DESKTOP
                    }
                    PirState.Enabled(type)
                } else {
                    PirState.Hidden
                }
            }
        }
    }

    private suspend fun isPirAvailable(): Boolean {
        return subscriptions.getAvailableProducts().contains(PIR)
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }
}
