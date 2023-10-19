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
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.FinishSignOut
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.SignOut
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.SignOutWithSync
import com.duckduckgo.sync.api.DeviceSyncState
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SubscriptionSettingsViewModel @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val deviceSyncState: DeviceSyncState,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    fun onSignOut() {
        viewModelScope.launch {
            val signOutCommand = if (deviceSyncState.isUserSignedInOnDevice()) {
                SignOutWithSync
            } else {
                SignOut
            }
            command.send(signOutCommand)
        }
    }

    fun removeFromDevice() {
        viewModelScope.launch {
            subscriptionsManager.signOut()
            command.send(FinishSignOut)
        }
    }

    sealed class Command {
        object FinishSignOut : Command()
        object SignOutWithSync : Command()
        object SignOut : Command()
    }
}
