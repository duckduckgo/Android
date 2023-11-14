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
import com.duckduckgo.sync.impl.SyncAccountRepository
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class ShowCodeViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val clipboard: Clipboard,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = command.receiveAsFlow()

    private val viewState = MutableStateFlow(ViewState())

    fun viewState(): Flow<ViewState> = viewState.onStart {
        val code = syncAccountRepository.getRecoveryCode()
        if (code == null) { // It shouldn't be null, but recovery code returns nullable.
            command.send(Command.Error)
        } else {
            viewState.emit(viewState.value.copy(code = code))
        }
    }

    data class ViewState(
        val code: String = "",
    )

    sealed class Command {
        object Error : Command()
    }

    fun onCopyCodeClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val recoveryCode = syncAccountRepository.getRecoveryCode() ?: return@launch
            clipboard.copyToClipboard(recoveryCode)
        }
    }
}
