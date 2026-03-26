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

package com.duckduckgo.sync.impl.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.ui.setup.SyncDeviceConnectedViewModel.Command.FinishSetupFlow
import com.duckduckgo.sync.impl.ui.setup.SyncDeviceConnectedViewModel.Command.LaunchSyncGetOnOtherPlatforms
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.*

@ContributesViewModel(ActivityScope::class)
class SyncDeviceConnectedViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val command = Channel<Command>(1, DROP_OLDEST)

    fun commands(): Flow<Command> = command.receiveAsFlow()
    data class ViewState(val deviceName: String)

    sealed class Command {
        data object FinishSetupFlow : Command()
        data object Error : Command()
        data object LaunchSyncGetOnOtherPlatforms : Command()
    }

    fun onDoneClicked() {
        viewModelScope.launch(dispatchers.main()) {
            command.send(FinishSetupFlow)
        }
    }

    fun onGetAppOnOtherDevicesClicked() {
        viewModelScope.launch(dispatchers.main()) {
            command.send(LaunchSyncGetOnOtherPlatforms)
        }
    }
}
