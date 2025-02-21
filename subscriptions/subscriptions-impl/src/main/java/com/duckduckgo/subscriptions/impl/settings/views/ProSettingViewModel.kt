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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.ui.settings.SettingViewModel
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.YEARLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenBuyScreen
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenRestoreScreen
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenSettings
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.ViewState
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.ViewState.SubscriptionRegion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class ProSettingViewModel @Inject constructor(
    private val subscriptions: Subscriptions,
    private val subscriptionsManager: SubscriptionsManager,
    private val dispatcherProvider: DispatcherProvider,
    private val pixelSender: SubscriptionPixelSender,
) : SettingViewModel<Command, ViewState>(ViewState())  {

    sealed class Command {
        data object OpenSettings : Command()
        data object OpenBuyScreen : Command()
        data object OpenRestoreScreen : Command()
    }

    data class ViewState(
        val visible: Boolean = false,
        val status: SubscriptionStatus = UNKNOWN,
        val region: SubscriptionRegion? = null,
    ) {
        enum class SubscriptionRegion { US, ROW }
    }

    private val appTPPollJob = ConflatedJob()

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

    override fun getSearchMissViewState(): ViewState {
        return ViewState(
            visible = false,
        )
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        subscriptionsManager.subscriptionStatus
            .distinctUntilChanged()
            .onEach { subscriptionStatus ->
                val offer = subscriptionsManager.getSubscriptionOffer().firstOrNull()
                val region = when (offer?.planId) {
                    MONTHLY_PLAN_ROW, YEARLY_PLAN_ROW -> SubscriptionRegion.ROW
                    MONTHLY_PLAN_US, YEARLY_PLAN_US -> SubscriptionRegion.US
                    else -> null
                }
                _viewState.emit(viewState.value.copy(status = subscriptionStatus, region = region))
            }.launchIn(viewModelScope)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        viewModelScope.launch {
            _viewState.update {
                it.copy(
                    visible = subscriptions.isEligible()
                )
            }
        }
        startPollingSubscriptionEligibility()
    }

    private fun startPollingSubscriptionEligibility() {
        appTPPollJob += viewModelScope.launch(dispatcherProvider.io()) {
            while (isActive) {
                _viewState.update {
                    it.copy(
                        visible = subscriptions.isEligible()
                    )
                }
                delay(1_000)
            }
        }
    }

    private fun sendCommand(newCommand: Command) {
        _commands.trySend(newCommand)
    }
}
