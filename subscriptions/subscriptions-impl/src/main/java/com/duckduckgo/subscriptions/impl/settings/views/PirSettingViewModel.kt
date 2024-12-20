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
import com.duckduckgo.subscriptions.api.Product.PIR
import com.duckduckgo.subscriptions.impl.ProductSubscriptionManager
import com.duckduckgo.subscriptions.impl.ProductSubscriptionManager.ProductStatus.ACTIVE
import com.duckduckgo.subscriptions.impl.ProductSubscriptionManager.ProductStatus.EXPIRED
import com.duckduckgo.subscriptions.impl.ProductSubscriptionManager.ProductStatus.INACTIVE
import com.duckduckgo.subscriptions.impl.ProductSubscriptionManager.ProductStatus.INELIGIBLE
import com.duckduckgo.subscriptions.impl.ProductSubscriptionManager.ProductStatus.SIGNED_OUT
import com.duckduckgo.subscriptions.impl.ProductSubscriptionManager.ProductStatus.WAITING
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.Command.OpenPir
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.ViewState.PirState
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ViewScope::class)
class PirSettingViewModel @Inject constructor(
    private val productSubscriptionManager: ProductSubscriptionManager,
    private val pixelSender: SubscriptionPixelSender,
) : ViewModel(), DefaultLifecycleObserver {

    sealed class Command {
        data object OpenPir : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    data class ViewState(val pirState: PirState = PirState.Hidden) {

        sealed class PirState {

            data object Hidden : PirState()
            data object Subscribed : PirState()
            data object Expired : PirState()
            data object Activating : PirState()
        }
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    fun onPir() {
        pixelSender.reportAppSettingsPirClick()
        sendCommand(OpenPir)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        productSubscriptionManager.entitlementStatus(PIR).onEach { status ->
            val pirState = when (status) {
                ACTIVE -> PirState.Subscribed
                INACTIVE, EXPIRED -> PirState.Expired
                WAITING -> PirState.Activating
                SIGNED_OUT, INELIGIBLE -> PirState.Hidden
            }

            _viewState.update { it.copy(pirState = pirState) }
        }.launchIn(viewModelScope)
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }
}
