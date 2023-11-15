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

package com.duckduckgo.sync.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class EnterCodeViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val clipboard: Clipboard,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    private val viewState = MutableStateFlow(ViewState())

    fun viewState(): Flow<ViewState> = viewState

    data class ViewState(
        val code: String = "",
        val authState: AuthState = AuthState.Idle,
    )

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        object Error : AuthState()
    }

    sealed class Command {
        object LoginSucess : Command()
    }

    fun onPasteCodeClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val pastedCode = clipboard.pasteFromClipboard()
            viewState.value = viewState.value.copy(code = pastedCode, authState = AuthState.Loading)
            authFlow(pastedCode)
        }
    }

    private suspend fun authFlow(
        pastedCode: String,
    ) {
        val result = syncAccountRepository.processCode(pastedCode)
        when (result) {
            is Result.Success -> command.send(Command.LoginSucess)
            is Result.Error -> {
                viewState.value = viewState.value.copy(authState = AuthState.Error)
            }
        }
    }
}
