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

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.RecoveryCodePDF
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.getOrNull
import com.duckduckgo.sync.impl.onFailure
import com.duckduckgo.sync.impl.onSuccess
import com.duckduckgo.sync.impl.pixels.SyncAccountOperation
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command.Next
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.ViewMode.CreatingAccount
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.ViewMode.SignedIn
import java.io.File
import javax.inject.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SaveRecoveryCodeViewModel @Inject constructor(
    private val recoveryCodePDF: RecoveryCodePDF,
    private val syncAccountRepository: SyncAccountRepository,
    private val clipboard: Clipboard,
    private val dispatchers: DispatcherProvider,
    private val syncPixels: SyncPixels,
) : ViewModel() {

    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart { createAccount() }

    private fun createAccount() = viewModelScope.launch(dispatchers.io()) {
        if (syncAccountRepository.isSignedIn()) {
            syncAccountRepository.getRecoveryCode().getOrNull()?.let { recoveryCode ->
                val newState = SignedIn(
                    b64RecoveryCode = recoveryCode.rawCode,
                )
                viewState.emit(ViewState(newState))
            } ?: command.send(Command.FinishWithError)
        } else {
            viewState.emit(ViewState(CreatingAccount))
            syncAccountRepository.createAccount().onFailure {
                command.send(Command.ShowError(R.string.sync_create_account_generic_error))
            }.onSuccess {
                syncAccountRepository.getRecoveryCode().getOrNull()?.let { recoveryCode ->
                    val newState = SignedIn(
                        b64RecoveryCode = recoveryCode.rawCode,
                    )
                    viewState.emit(ViewState(newState))
                } ?: command.send(Command.FinishWithError)
            }
        }
    }

    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val viewMode: ViewMode = CreatingAccount,
    )

    sealed class ViewMode {
        data object CreatingAccount : ViewMode()
        data class SignedIn(
            val b64RecoveryCode: String,
        ) : ViewMode()
    }

    sealed class Command {
        data object Next : Command()
        data class ShowMessage(val message: Int) : Command()
        data object FinishWithError : Command()
        data object CheckIfUserHasStoragePermission : Command()
        data class RecoveryCodePDFSuccess(val recoveryCodePDFFile: File) : Command()
        data class ShowError(@StringRes val message: Int, val reason: String = "") : Command()
    }

    fun onNextClicked() {
        viewModelScope.launch {
            command.send(Next)
        }
    }

    fun onCopyCodeClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val authCode = syncAccountRepository.getRecoveryCode().getOrNull() ?: return@launch
            clipboard.copyToClipboard(authCode.rawCode)
            command.send(ShowMessage(R.string.sync_code_copied_message))
        }
    }

    fun onSaveRecoveryCodeClicked() {
        viewModelScope.launch {
            command.send(CheckIfUserHasStoragePermission)
        }
    }

    fun generateRecoveryCode(viewContext: Context) {
        viewModelScope.launch(dispatchers.io()) {
            syncAccountRepository.getRecoveryCode()
                .onSuccess { authCode ->
                    kotlin.runCatching {
                        recoveryCodePDF.generateAndStoreRecoveryCodePDF(viewContext, authCode.rawCode)
                    }.onSuccess { generateRecoveryCodePDF ->
                        command.send(RecoveryCodePDFSuccess(generateRecoveryCodePDF))
                    }.onFailure {
                        syncPixels.fireSyncAccountErrorPixel(Error(reason = it.message.toString()), type = SyncAccountOperation.CREATE_PDF)
                        command.send(Command.ShowError(R.string.sync_recovery_pdf_error))
                    }
                }.onFailure {
                    command.send(Command.ShowError(R.string.sync_recovery_pdf_error))
                }
        }
    }

    fun onErrorDialogDismissed() {
        viewModelScope.launch(dispatchers.io()) {
            command.send(Command.FinishWithError)
        }
    }
}
