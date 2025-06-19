/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.subscription

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.subscription.DuckAiPlusSettingsViewModel.ViewState.SettingState
import com.duckduckgo.duckchat.impl.subscription.DuckAiPlusSettingsViewModel.ViewState.SettingState.Disabled
import com.duckduckgo.duckchat.impl.subscription.DuckAiPlusSettingsViewModel.ViewState.SettingState.Hidden
import com.duckduckgo.subscriptions.api.Product.DuckAiPlus
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import javax.inject.Inject
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

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class DuckAiPlusSettingsViewModel @Inject constructor(
    private val subscriptions: Subscriptions,
    private val duckChatFeature: DuckChatFeature,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    sealed class Command {
        data object OpenDuckAiPlusSettings : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    data class ViewState(val settingState: SettingState = Hidden) {

        sealed class SettingState {

            data object Hidden : SettingState()
            data object Enabled : SettingState()
            data object Disabled : SettingState()
        }
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    fun onDuckAiClicked() {
        sendCommand(Command.OpenDuckAiPlusSettings)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        viewModelScope.launch(dispatcherProvider.io()) {
            if (duckChatFeature.duckAiPlus().isEnabled().not()) {
                _viewState.update { it.copy(settingState = Hidden) }
                return@launch
            }

            subscriptions.getEntitlementStatus().map { entitlements ->
                entitlements.any { product ->
                    product == DuckAiPlus
                }
            }.onEach { hasValidEntitlement ->
                val subscriptionStatus = subscriptions.getSubscriptionStatus()
                val state = getDuckAiProState(hasValidEntitlement, subscriptionStatus)
                _viewState.update { it.copy(settingState = state) }
            }.launchIn(viewModelScope)
        }
    }

    private suspend fun getDuckAiProState(
        hasValidEntitlement: Boolean,
        subscriptionStatus: SubscriptionStatus,
    ): SettingState {
        return when (subscriptionStatus) {
            SubscriptionStatus.UNKNOWN -> Hidden
            SubscriptionStatus.INACTIVE,
            SubscriptionStatus.EXPIRED,
            SubscriptionStatus.WAITING,
            -> {
                if (isDuckAiProAvailable()) {
                    Disabled
                } else {
                    Hidden
                }
            }

            SubscriptionStatus.AUTO_RENEWABLE,
            SubscriptionStatus.NOT_AUTO_RENEWABLE,
            SubscriptionStatus.GRACE_PERIOD,
            -> {
                if (hasValidEntitlement) {
                    SettingState.Enabled
                } else {
                    Hidden
                }
            }
        }
    }

    private suspend fun isDuckAiProAvailable(): Boolean {
        return subscriptions.getAvailableProducts()
            .any { availableProduct ->
                availableProduct == DuckAiPlus
            }
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }
}
