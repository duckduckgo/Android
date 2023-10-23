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

package com.duckduckgo.networkprotection.subscription.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.subscription.NetpSubscriptionManager
import com.duckduckgo.networkprotection.subscription.NetpSubscriptionManager.NetpAuthorizationStatus.NoValidPAT
import com.duckduckgo.networkprotection.subscription.NetpSubscriptionManager.NetpAuthorizationStatus.Success
import com.duckduckgo.networkprotection.subscription.NetpSubscriptionManager.NetpAuthorizationStatus.UnableToAuthorize
import com.duckduckgo.networkprotection.subscription.NetpSubscriptionManager.NetpAuthorizationStatus.Unknown
import com.duckduckgo.networkprotection.subscription.R
import com.duckduckgo.networkprotection.subscription.ui.NetpVerifySubscriptionViewModel.Command.LaunchNetPScreen
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class NetpVerifySubscriptionViewModel @Inject constructor(
    private val netpSubscriptionManager: NetpSubscriptionManager,
    private val dispatchersProvider: DispatcherProvider,
) : ViewModel() {
    private val mutableViewState = MutableStateFlow(ViewState(R.string.netpVerifySubscriptionInProgress))
    val viewState: Flow<ViewState> = mutableViewState.asStateFlow()

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    data class ViewState(@StringRes val message: Int)

    sealed class Command {
        object LaunchNetPScreen : Command()
    }

    fun start() {
        viewModelScope.launch {
            netpSubscriptionManager.getState()
                .flowOn(dispatchersProvider.io())
                .collectLatest {
                    logcat { "Netp Auth: state received $it" }
                    when (it) {
                        Success -> commandChannel.send(LaunchNetPScreen)
                        Unknown -> mutableViewState.emit(ViewState(R.string.netpVerifySubscriptionInProgress))
                        NoValidPAT -> mutableViewState.emit(ViewState(R.string.netpVerifySubscriptionNoSubscription))
                        is UnableToAuthorize -> {
                            mutableViewState.emit(ViewState(R.string.netpVerifySubscriptionNoEntitlement))
                            logcat { "Netp auth: No entitlement cause: ${it.message}" }
                        }
                    }
                }
        }
        attemptToAuthorize()
    }

    private fun attemptToAuthorize() {
        viewModelScope.launch {
            netpSubscriptionManager.authorize()
        }
    }
}
