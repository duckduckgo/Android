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

package com.duckduckgo.sync.impl.promotion.devicemigration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.sync.impl.promotion.devicemigration.SyncAfterDeviceTransferViewModel.Command.Hide
import com.duckduckgo.sync.impl.promotion.devicemigration.SyncAfterDeviceTransferViewModel.Command.OpenSyncSetup
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityStartSyncFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ViewScope::class)
class SyncAfterDeviceTransferViewModel @Inject constructor(
    private val prompt: SyncAfterDeviceTransferPrompt,
) : ViewModel() {

    sealed interface Command {
        data class OpenSyncSetup(val params: ActivityParams) : Command
        data object Hide : Command
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    // Not dismissed here: the card keeps offering Sync if the user backs out of setup, and stops once Sync is on.
    fun onTurnOnSyncClicked() {
        viewModelScope.launch {
            command.send(OpenSyncSetup(SyncActivityStartSyncFlow(source = null)))
        }
    }

    fun onDismissClicked() {
        viewModelScope.launch {
            prompt.dismiss()
            command.send(Hide)
        }
    }
}
