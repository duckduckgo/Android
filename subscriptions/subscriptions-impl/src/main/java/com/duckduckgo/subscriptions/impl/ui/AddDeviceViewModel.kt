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

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.Command.AddEmail
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.Command.Error
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.Command.ManageEmail
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ActivityScope::class)
class AddDeviceViewModel @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val dispatcherProvider: DispatcherProvider,
    private val pixelSender: SubscriptionPixelSender,
) : ViewModel(), DefaultLifecycleObserver {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()
    data class ViewState(
        val email: String? = null,
    )

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        subscriptionsManager.subscriptionStatus
            .onEach {
                emitChanges()
            }.launchIn(viewModelScope)
    }

    private suspend fun emitChanges() {
        var email: String? = null
        subscriptionsManager.getAccount()?.let {
            if (!it.email.isNullOrBlank()) {
                email = it.email
            }
        }
        _viewState.emit(viewState.value.copy(email = email))
    }

    fun useEmail() {
        pixelSender.reportAddDeviceEnterEmailClick()

        viewModelScope.launch(dispatcherProvider.io()) {
            val account = subscriptionsManager.getAccount()
            if (account != null) {
                if (account.email.isNullOrBlank()) {
                    command.send(AddEmail)
                } else {
                    command.send(ManageEmail)
                }
            } else {
                command.send(Error)
            }
        }
    }

    sealed class Command {
        object AddEmail : Command()
        object ManageEmail : Command()
        object Error : Command()
    }
}
