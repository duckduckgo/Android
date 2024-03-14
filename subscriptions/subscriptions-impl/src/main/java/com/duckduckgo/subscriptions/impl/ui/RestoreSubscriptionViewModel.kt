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
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.RecoverSubscriptionResult
import com.duckduckgo.subscriptions.impl.SubscriptionsChecker
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
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
    private val subscriptionsChecker: SubscriptionsChecker,
    private val dispatcherProvider: DispatcherProvider,
    private val pixelSender: SubscriptionPixelSender,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()
    data class ViewState(
        val email: String? = null,
    )

    fun restoreFromStore() {
        pixelSender.reportActivateSubscriptionRestorePurchaseClick()
        viewModelScope.launch(dispatcherProvider.io()) {
            when (val subscription = subscriptionsManager.recoverSubscriptionFromStore()) {
                is RecoverSubscriptionResult.Success -> {
                    if (subscription.subscription.isActive()) {
                        subscriptionsChecker.runChecker()
                        pixelSender.reportRestoreUsingStoreSuccess()
                        pixelSender.reportSubscriptionActivated()
                        command.send(Success)
                    } else {
                        pixelSender.reportRestoreUsingStoreFailureSubscriptionNotFound()
                        subscriptionsManager.signOut()
                        command.send(SubscriptionNotFound)
                    }
                }
                is RecoverSubscriptionResult.Failure -> {
                    when (subscription.message) {
                        SUBSCRIPTION_NOT_FOUND_ERROR -> {
                            pixelSender.reportRestoreUsingStoreFailureSubscriptionNotFound()
                            command.send(SubscriptionNotFound)
                        }
                        else -> {
                            pixelSender.reportRestoreUsingStoreFailureOther()
                            command.send(Error)
                        }
                    }
                }
            }
        }
    }

    fun restoreFromEmail() {
        pixelSender.reportActivateSubscriptionEnterEmailClick()
        viewModelScope.launch {
            command.send(RestoreFromEmail)
        }
    }

    sealed class Command {
        data object RestoreFromEmail : Command()
        data object Success : Command()
        data object SubscriptionNotFound : Command()
        data object Error : Command()
    }
}
