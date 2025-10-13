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

package com.duckduckgo.subscriptions.impl.settings.views

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.subscriptions.api.Product.ITR
import com.duckduckgo.subscriptions.api.Product.ROW_ITR
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingViewModel.Command.OpenItr
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingViewModel.ViewState.ItrState
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
class ItrSettingViewModel @Inject constructor(
    private val subscriptions: Subscriptions,
    private val pixelSender: SubscriptionPixelSender,
) : ViewModel(), DefaultLifecycleObserver {

    sealed class Command {
        data object OpenItr : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    data class ViewState(val itrState: ItrState = ItrState.Hidden) {

        sealed class ItrState {

            data object Hidden : ItrState()
            data object Enabled : ItrState()
            data object Disabled : ItrState()
        }
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    fun onItr() {
        pixelSender.reportAppSettingsIdtrClick()
        sendCommand(OpenItr)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        subscriptions.getEntitlementStatus()
            .map { entitledProducts ->
                entitledProducts.any { product -> product == ITR || product == ROW_ITR }
            }
            .onEach { hasValidEntitlement ->
                val subscriptionStatus = subscriptions.getSubscriptionStatus()

                val itrState = getItrState(hasValidEntitlement, subscriptionStatus)

                _viewState.update { it.copy(itrState = itrState) }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun getItrState(
        hasValidEntitlement: Boolean,
        subscriptionStatus: SubscriptionStatus,
    ): ItrState {
        return when (subscriptionStatus) {
            SubscriptionStatus.UNKNOWN -> ItrState.Hidden

            SubscriptionStatus.INACTIVE,
            SubscriptionStatus.EXPIRED,
            SubscriptionStatus.WAITING,
            -> {
                if (isItrAvailable()) {
                    ItrState.Disabled
                } else {
                    ItrState.Hidden
                }
            }

            SubscriptionStatus.AUTO_RENEWABLE,
            SubscriptionStatus.NOT_AUTO_RENEWABLE,
            SubscriptionStatus.GRACE_PERIOD,
            -> {
                if (hasValidEntitlement) {
                    ItrState.Enabled
                } else {
                    ItrState.Hidden
                }
            }
        }
    }

    private suspend fun isItrAvailable(): Boolean {
        return subscriptions.getAvailableProducts().any { feature -> feature == ITR || feature == ROW_ITR }
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }
}
