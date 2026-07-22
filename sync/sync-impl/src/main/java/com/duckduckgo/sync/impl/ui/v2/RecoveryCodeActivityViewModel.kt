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

package com.duckduckgo.sync.impl.ui.v2

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
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.pixels.SyncAccountOperation
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class RecoveryCodeActivityViewModel @Inject constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val syncAutoRestoreManager: SyncAutoRestoreManager,
    private val recoveryCodePDF: RecoveryCodePDF,
    private val clipboard: Clipboard,
    private val syncPixels: SyncPixels,
    private val syncSetupWideEvent: SyncSetupWideEvent,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    private val _commands = Channel<Command>(Channel.BUFFERED)
    val commands = _commands.receiveAsFlow()

    init {
        initializeRecoveryCode()
        initializeAutoRestoreAvailability()
    }

    // Deliberately a suspend function instead of launching in the ViewModel scope and holding onto a Context.
    // A ViewModel outlives the Activity across configuration changes, so retaining an Activity Context would
    // leak the destroyed Activity. Suspending lets the caller run this from a lifecycle-scoped coroutine that
    // is canceled on Activity destruction, and passing the Context in at call time ensures it is never held
    // beyond the call.
    suspend fun generateRecoveryCodeSheet(context: Context) {
        val code = viewState.first { it.recoveryCode != null }.recoveryCode ?: return
        try {
            val file = withContext(dispatchers.io()) { recoveryCodePDF.generateAndStoreRecoveryCodePDF(context, code) }
            _commands.send(Command.ShareRecoveryCodeFile(file))
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            syncPixels.fireSyncAccountErrorPixel(
                result = Error(reason = e.message.orEmpty()),
                type = SyncAccountOperation.CREATE_PDF,
            )
            _commands.send(Command.ShowError(R.string.sync_recovery_pdf_error))
        }
    }

    fun changeRestoreOnReinstall(enabled: Boolean) {
        _viewState.update { it.copy(isAutoRestoreEnabled = enabled) }
    }

    fun showMessage(@StringRes message: Int) {
        viewModelScope.launch {
            _commands.send(Command.ShowMessage(message))
        }
    }

    fun onCopyCodeClicked() {
        viewModelScope.launch {
            val code = viewState.first { it.recoveryCode != null }.recoveryCode ?: return@launch
            clipboard.copyToClipboard(code)
            showMessage(R.string.sync_code_copied_message)
        }
    }

    fun onDownloadCodeClicked() {
        viewModelScope.launch {
            _commands.send(Command.CheckStoragePermission)
        }
    }

    fun onDoneClicked() {
        viewModelScope.launch {
            _commands.send(Command.Close)
        }
    }

    fun onErrorDialogDismissed() {
        viewModelScope.launch {
            _commands.send(Command.Close)
        }
    }

    private fun initializeRecoveryCode() {
        suspend fun getAndShowRecoveryCode() {
            when (val result = syncAccountRepository.getRecoveryCode()) {
                is Success<AuthCode> -> {
                    syncSetupWideEvent.onRecoveryCodeShown()
                    _viewState.update { it.copy(recoveryCode = result.data.rawCode) }
                }

                is Error -> {
                    syncSetupWideEvent.onRecoveryCodeGenerationFailed()
                    _commands.send(Command.ShowError(R.string.sync_device_v2_recovery_code_get_code_error, result.reason))
                }
            }
        }

        viewModelScope.launch(dispatchers.io()) {
            if (syncAccountRepository.isSignedIn()) {
                getAndShowRecoveryCode()
            } else {
                when (syncAccountRepository.createAccount()) {
                    is Success<*> -> {
                        getAndShowRecoveryCode()
                    }

                    is Error -> {
                        _commands.send(Command.ShowError(R.string.sync_create_account_generic_error))
                    }
                }
            }
        }
    }

    private fun initializeAutoRestoreAvailability() {
        viewModelScope.launch {
            val isAutoRestoreAvailable = syncAutoRestoreManager.isAutoRestoreAvailable()
            _viewState.update { it.copy(isAutoRestoreAvailable = isAutoRestoreAvailable) }
        }
    }

    data class ViewState(
        val recoveryCode: String? = null,
        val isAutoRestoreEnabled: Boolean = true,
        val isAutoRestoreAvailable: Boolean = false,
    )

    sealed interface Command {
        data class ShareRecoveryCodeFile(
            val pdfFile: File,
        ) : Command

        data object CheckStoragePermission : Command

        data class ShowMessage(
            @StringRes val message: Int,
        ) : Command

        data class ShowError(
            @StringRes val message: Int,
            val reason: String? = "",
        ) : Command

        data object Close : Command
    }
}
