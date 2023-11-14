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

package com.duckduckgo.subscriptions.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.Companion.SUBSCRIPTION_NOT_FOUND_ERROR
import com.duckduckgo.subscriptions.impl.SubscriptionsData
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.Error
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.RestoreFromEmail
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.SubscriptionNotFound
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.Success
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class RestoreSubscriptionViewModel @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()
    data class ViewState(
        val email: String? = null,
    )

    fun restoreFromStore() {
        viewModelScope.launch(dispatcherProvider.io()) {
            when (val response = subscriptionsManager.recoverSubscriptionFromStore()) {
                is SubscriptionsData.Success -> {
                    if (response.entitlements.isEmpty()) {
                        subscriptionsManager.signOut()
                        command.send(SubscriptionNotFound)
                    } else {
                        command.send(Success)
                    }
                }
                is SubscriptionsData.Failure -> {
                    when (response.message) {
                        SUBSCRIPTION_NOT_FOUND_ERROR -> command.send(SubscriptionNotFound)
                        else -> command.send(Error(response.message))
                    }
                }
            }
        }
    }

    fun restoreFromEmail() {
        viewModelScope.launch {
            command.send(RestoreFromEmail)
        }
    }

    sealed class Command {
        object RestoreFromEmail : Command()
        object Success : Command()
        object SubscriptionNotFound : Command()
        data class Error(val message: String) : Command()
    }
}
