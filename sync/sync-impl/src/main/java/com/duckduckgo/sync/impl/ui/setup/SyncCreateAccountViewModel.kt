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
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.Command.FinishSetupFlow
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.ViewMode.CreatingAccount
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.ViewMode.SignedIn
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SyncCreateAccountViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart { createAccount() }

    fun commands(): Flow<Command> = command.receiveAsFlow()

    sealed class Command {
        object FinishSetupFlow : Command()
        object AbortFlow : Command()
        object Error : Command()
    }

    data class ViewState(
        val viewMode: ViewMode = CreatingAccount,
    )

    sealed class ViewMode {
        object CreatingAccount : ViewMode()
        object SignedIn : ViewMode()
    }

    private fun createAccount() = viewModelScope.launch(dispatchers.io()) {
        viewState.emit(ViewState(CreatingAccount))
        when (syncAccountRepository.createAccount()) {
            is Error -> {
                command.send(Command.Error)
            }

            is Success -> {
                command.send(FinishSetupFlow)
            }
        }
    }
}
