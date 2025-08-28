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

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.onFailure
import com.duckduckgo.sync.impl.onSuccess
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.Command.FinishSetupFlow
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.ViewMode.CreatingAccount
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
    private val syncPixels: SyncPixels,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)

    private val viewState = MutableStateFlow(ViewState())
    fun viewState(source: String?): Flow<ViewState> = viewState.onStart { createAccount(source) }
    fun commands(): Flow<Command> = command.receiveAsFlow()

    sealed class Command {
        data object FinishSetupFlow : Command()
        data object AbortFlow : Command()
        data object Error : Command()
        data class ShowError(@StringRes val message: Int, val reason: String? = "") : Command()
    }

    data class ViewState(
        val viewMode: ViewMode = CreatingAccount,
    )

    sealed class ViewMode {
        data object CreatingAccount : ViewMode()
        data object SignedIn : ViewMode()
    }

    private fun createAccount(source: String?) = viewModelScope.launch(dispatchers.io()) {
        viewState.emit(ViewState(CreatingAccount))
        if (syncAccountRepository.isSignedIn()) {
            command.send(FinishSetupFlow)
        } else {
            syncAccountRepository.createAccount().onSuccess {
                syncPixels.fireSignupDirectPixel(source)
                command.send(FinishSetupFlow)
            }.onFailure {
                command.send(Command.ShowError(R.string.sync_create_account_generic_error, it.reason))
            }
        }
    }

    fun onErrorDialogDismissed() {
        viewModelScope.launch(dispatchers.io()) {
            command.send(Command.AbortFlow)
        }
    }
}
