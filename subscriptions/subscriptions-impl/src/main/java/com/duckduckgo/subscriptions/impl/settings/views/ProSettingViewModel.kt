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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenBuyScreen
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenSettings
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
class ProSettingViewModel @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel(), DefaultLifecycleObserver {

    sealed class Command {
        data object OpenSettings : Command()
        data object OpenBuyScreen : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()
    data class ViewState(val hasSubscription: Boolean = false)

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    fun onSettings() {
        sendCommand(OpenSettings)
    }

    fun onBuy() {
        sendCommand(OpenBuyScreen)
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        viewModelScope.launch(dispatcherProvider.io()) {
            subscriptionsManager.hasSubscription.collect {
                _viewState.emit(viewState.value.copy(hasSubscription = it))
            }
        }
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            command.send(newCommand)
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val proSettingViewModel: Provider<ProSettingViewModel>,
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return with(modelClass) {
                when {
                    isAssignableFrom(ProSettingViewModel::class.java) -> proSettingViewModel.get()
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            } as T
        }
    }
}
