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

package com.duckduckgo.sync.impl.ui.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.sync.impl.ConnectedDevice
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class EditDeviceViewModel @AssistedInject constructor(
    @Assisted private val device: ConnectedDevice,
) : ViewModel() {
    private val _viewState = MutableStateFlow(
        ViewState(device = device),
    )
    val viewState: Flow<ViewState> = _viewState.asStateFlow()

    private val _commands = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    fun onCloseClicked() {
        viewModelScope.launch {
            _commands.send(Command.Close)
        }
    }

    data class ViewState(
        val device: ConnectedDevice,
    )

    sealed class Command {
        data object Close : Command()
    }

    @AssistedFactory
    interface Factory {
        fun create(device: ConnectedDevice): EditDeviceViewModel

        class Provider(
            private val assistedFactory: Factory,
            private val device: ConnectedDevice,
        ) : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(device) as T
            }
        }
    }
}
