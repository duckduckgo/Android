/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.api.AutofillImportLaunchSource.InBrowserPromo
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult
import com.duckduckgo.autofill.impl.importing.credentialtransfer.CredentialExchangeImportResult
import com.duckduckgo.autofill.impl.importing.credentialtransfer.CredentialExchangePasswordImporter
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.ImportPasswordsPixelSender
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.BrowserPromoPreImport
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.DeterminingFirstView
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.Importing
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.PreImport
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.promptscoordinator.api.PromptExposureReporter
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class ImportFromGooglePasswordsDialogViewModel @Inject constructor(
    private val credentialImporter: CredentialImporter,
    private val dispatchers: DispatcherProvider,
    private val importPasswordsPixelSender: ImportPasswordsPixelSender,
    private val autofillStore: InternalAutofillStore,
    private val promptExposureReporter: PromptExposureReporter,
    private val credentialExchangePasswordImporter: CredentialExchangePasswordImporter,
) : ViewModel() {

    fun onImportFlowFinishedSuccessfully() {
        viewModelScope.launch(dispatchers.main()) {
            observeImportJob()
        }
    }

    private suspend fun observeImportJob() {
        credentialImporter.getImportStatus().collect {
            when (it) {
                is ImportResult.InProgress -> {
                    logcat { "Import in progress" }
                    _viewState.value = viewState.value.copy(viewMode = Importing)
                }

                is ImportResult.Finished -> {
                    logcat { "Import finished: ${it.savedCredentials} imported. ${it.numberSkipped} skipped." }
                    _viewState.value = viewState.value.copy(viewMode = ViewMode.ImportSuccess(it))
                }
            }
        }
    }

    fun onImportFlowFinishedWithError() {
        _viewState.value = viewState.value.copy(viewMode = ViewMode.ImportError)
    }

    fun onImportButtonClicked() {
        sendStartImportCommand(viewState.value.usesCredentialExchange)
    }

    fun onDirectImportRequested() {
        viewModelScope.launch {
            sendStartImportCommand(credentialExchangePasswordImporter.isSupported())
        }
    }

    private fun sendStartImportCommand(useCredentialExchange: Boolean) {
        if (!useCredentialExchange) {
            command.trySend(Command.StartWebFlow)
            return
        }
        if (credentialExchangeInProgress) return
        credentialExchangeInProgress = true
        command.trySend(Command.StartCredentialExchange)
    }

    fun onCredentialExchangeFinished(
        result: CredentialExchangeResult,
        importSource: AutofillImportLaunchSource,
        canShowPreImportDialog: Boolean,
    ) {
        credentialExchangeInProgress = false
        viewModelScope.launch {
            when (val converted = credentialExchangePasswordImporter.convertAndDeduplicate(result)) {
                is CredentialExchangeImportResult.Success -> {
                    credentialImporter.import(converted.credentials, converted.originalCount, importSource)
                    onImportFlowFinishedSuccessfully()
                }
                is CredentialExchangeImportResult.Cancelled -> onImportFlowCancelledByUser(canShowPreImportDialog)
                is CredentialExchangeImportResult.Failure -> {
                    logcat(WARN) { "Credential exchange failed (${converted.reason}), falling back to web flow" }
                    command.trySend(Command.StartWebFlow)
                }
            }
        }
    }

    fun onImportFlowCancelledByUser(canShowPreImportDialog: Boolean) {
        if (!canShowPreImportDialog) {
            _viewState.value = viewState.value.copy(viewMode = ViewMode.FlowTerminated)
        }
    }

    fun shouldShowInitialInstructionalPrompt(importSource: AutofillImportLaunchSource) {
        val viewMode = if (importSource == AutofillImportLaunchSource.InBrowserPromo) {
            logcat { "ImportFromGooglePasswordsDialogViewModel: InBrowserPromo scenario" }
            BrowserPromoPreImport
        } else {
            logcat { "ImportFromGooglePasswordsDialogViewModel: PreImport scenario" }
            PreImport
        }

        viewModelScope.launch(dispatchers.io()) {
            autofillStore.inBrowserImportPromoShownCount += 1
        }
        importPasswordsPixelSender.onImportPasswordsDialogDisplayed(importSource)
        // Only the password-field promo is app-originated; the other sources are opened by the user.
        if (importSource == AutofillImportLaunchSource.InBrowserPromo) {
            promptExposureReporter.reportPromptShown(IMPORT_PASSWORDS_GOOGLE_PROMPT_ID)
        }

        viewModelScope.launch {
            val usesCredentialExchange = credentialExchangePasswordImporter.isSupported()
            _viewState.value = viewState.value.copy(viewMode = viewMode, usesCredentialExchange = usesCredentialExchange)
        }
    }

    fun onInBrowserPromoDismissed() {
        viewModelScope.launch(dispatchers.io()) {
            autofillStore.hasDeclinedInBrowserPasswordImportPromo = true
            importPasswordsPixelSender.onUserCancelledImportPasswordsDialog(InBrowserPromo)
        }
        _viewState.value = viewState.value.copy(viewMode = ViewMode.FlowTerminated)
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState

    private val command = Channel<Command>(1, DROP_OLDEST)
    private var credentialExchangeInProgress = false
    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val viewMode: ViewMode = DeterminingFirstView,
        val usesCredentialExchange: Boolean = false,
    )

    sealed interface Command {
        data object StartWebFlow : Command
        data object StartCredentialExchange : Command
    }

    sealed interface ViewMode {
        data object DeterminingFirstView : ViewMode
        data object PreImport : ViewMode
        data object BrowserPromoPreImport : ViewMode
        data object Importing : ViewMode
        data class ImportSuccess(val importResult: ImportResult.Finished) : ViewMode
        data object ImportError : ViewMode
        data object FlowTerminated : ViewMode
    }

    private companion object {
        const val IMPORT_PASSWORDS_GOOGLE_PROMPT_ID = "import_passwords_google"
    }
}
