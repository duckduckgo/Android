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
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenBuyScreen
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenRestoreScreen
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenSettings
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.ViewState.SubscriptionRegion
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class ProSettingViewModel @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val pixelSender: SubscriptionPixelSender,
) : ViewModel(), DefaultLifecycleObserver {

    sealed class Command {
        data object OpenSettings : Command()
        data object OpenBuyScreen : Command()
        data object OpenRestoreScreen : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    data class ViewState(
        val status: SubscriptionStatus = UNKNOWN,
        val region: SubscriptionRegion? = null,
    ) {
        enum class SubscriptionRegion { US, ROW }
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    fun onSettings() {
        sendCommand(OpenSettings)
    }

    fun onBuy() {
        sendCommand(OpenBuyScreen)
    }

    fun onRestore() {
        pixelSender.reportAppSettingsRestorePurchaseClick()
        sendCommand(OpenRestoreScreen)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        subscriptionsManager.subscriptionStatus
            .distinctUntilChanged()
            .onEach { subscriptionStatus ->
                val region = when (subscriptionsManager.getSubscriptionOffer()?.monthlyPlanId) {
                    MONTHLY_PLAN_ROW -> SubscriptionRegion.ROW
                    MONTHLY_PLAN_US -> SubscriptionRegion.US
                    else -> null
                }
                _viewState.emit(viewState.value.copy(status = subscriptionStatus, region = region))
            }.launchIn(viewModelScope)
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }
}
