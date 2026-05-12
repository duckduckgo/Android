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

package com.duckduckgo.sync.impl.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.setup.SyncPreviousSessionReadyViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.setup.SyncPreviousSessionReadyViewModel.Command.ContinueSetup
import com.duckduckgo.sync.impl.ui.setup.SyncPreviousSessionReadyViewModel.Command.StartRestore
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class SyncPreviousSessionReadyViewModel @Inject constructor(
    private val syncPixels: SyncPixels,
    private val syncSetupWideEvent: SyncSetupWideEvent,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)

    @Volatile private var source: String = ""

    fun commands(): Flow<Command> = command.receiveAsFlow()

    sealed class Command {
        data object StartRestore : Command()
        data object ContinueSetup : Command()
        data object Close : Command()
    }

    fun onScreenShown(source: String) {
        this.source = source
        syncPixels.fireAutoRestoreSettingsReadyShown(source)
    }

    fun onResumeClicked() {
        syncPixels.fireAutoRestoreSettingsRestoreTapped(source)
        viewModelScope.launch {
            syncSetupWideEvent.onSyncRestoreStarted()
            command.send(StartRestore)
        }
    }

    fun onContinueSetupClicked() {
        syncPixels.fireAutoRestoreSettingsSkipRestoreTapped(source)
        viewModelScope.launch {
            command.send(ContinueSetup)
        }
    }

    fun onCloseClicked() {
        syncPixels.fireAutoRestoreSettingsCancelled(source)
        viewModelScope.launch {
            command.send(Close)
        }
    }
}
