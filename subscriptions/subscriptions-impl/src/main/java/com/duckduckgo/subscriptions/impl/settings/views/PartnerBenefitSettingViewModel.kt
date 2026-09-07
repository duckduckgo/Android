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

package com.duckduckgo.subscriptions.impl.settings.views

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.internal.PartnershipsHubUrlProvider
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.isActive
import com.duckduckgo.subscriptions.impl.settings.views.PartnerBenefitSettingViewModel.Command.OpenPartnershipsHub
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver")
@ContributesViewModel(ViewScope::class)
class PartnerBenefitSettingViewModel @Inject constructor(
    private val subscriptions: Subscriptions,
    private val subscriptionsFeature: SubscriptionsFeature,
    private val partnershipsHubUrlProvider: PartnershipsHubUrlProvider,
    private val pixelSender: SubscriptionPixelSender,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(val isVisible: Boolean = false)

    sealed interface Command {
        data class OpenPartnershipsHub(val url: String) : Command
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        subscriptions.getSubscriptionStatusFlow()
            .distinctUntilChanged()
            .onEach { status ->
                val isVisible = status.isActive() && isFeatureEnabled()
                _viewState.update { it.copy(isVisible = isVisible) }
            }
            .launchIn(viewModelScope)
    }

    fun onPartnershipsHubClicked() {
        pixelSender.reportAppSettingsPartnerBenefitsClick()

        viewModelScope.launch {
            val url = withContext(dispatcherProvider.io()) { partnershipsHubUrlProvider.partnershipsHubUrl }
            command.send(OpenPartnershipsHub(url))
        }
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatcherProvider.io()) {
        subscriptionsFeature.partnershipsHub().isEnabled()
    }
}
