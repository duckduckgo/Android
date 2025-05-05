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
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsLaunchSource
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsLaunchSource.SOURCE_ACTIVATING
import com.duckduckgo.sync.impl.ui.setup.SyncSetupDeepLinkConnectedViewModel.Command.LaunchSyncGetOnOtherPlatforms
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SyncSetupDeepLinkConnectedViewModel @Inject constructor() : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)

    fun commands(): Flow<Command> = command.receiveAsFlow()

    sealed class Command {
        data object Close : Command()
        data class LaunchSyncGetOnOtherPlatforms(val source: SyncGetOnOtherPlatformsLaunchSource) : Command()
    }

    fun onBackPressed() {
        viewModelScope.launch {
            command.send(Command.Close)
        }
    }

    fun onSetupFinished() {
        viewModelScope.launch {
            command.send(Command.Close)
        }
    }

    fun onGetAppOnOtherDevicesClicked() {
        viewModelScope.launch {
            command.send(LaunchSyncGetOnOtherPlatforms(source = SOURCE_ACTIVATING))
        }
    }
}
