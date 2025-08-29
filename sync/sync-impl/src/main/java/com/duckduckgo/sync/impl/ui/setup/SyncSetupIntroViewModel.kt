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
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.SYNC_INTRO
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.Command.AbortFlow
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.Command.RecoverDataFlow
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.Command.StartSetupFlow
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.ViewMode.CreateAccountIntro
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.ViewMode.RecoverAccountIntro
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(FragmentScope::class)
class SyncSetupIntroViewModel @Inject constructor() : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(screen: Screen): Flow<ViewState> = viewState.onStart {
        val viewMode = when (screen) {
            SYNC_INTRO -> CreateAccountIntro
            else -> RecoverAccountIntro
        }
        viewState.emit(ViewState(viewMode))
    }

    fun commands(): Flow<Command> = command.receiveAsFlow()

    sealed class Command {
        data object StartSetupFlow : Command()
        data object RecoverDataFlow : Command()
        data object AbortFlow : Command()
    }

    data class ViewState(
        val viewMode: ViewMode = CreateAccountIntro,
    )

    sealed class ViewMode {
        data object CreateAccountIntro : ViewMode()
        data object RecoverAccountIntro : ViewMode()
    }

    fun onTurnSyncOnClicked() {
        viewModelScope.launch {
            command.send(StartSetupFlow)
        }
    }

    fun onStartRecoverDataClicked() {
        viewModelScope.launch {
            command.send(RecoverDataFlow)
        }
    }

    fun onAbortClicked() {
        viewModelScope.launch {
            command.send(AbortFlow)
        }
    }
}
