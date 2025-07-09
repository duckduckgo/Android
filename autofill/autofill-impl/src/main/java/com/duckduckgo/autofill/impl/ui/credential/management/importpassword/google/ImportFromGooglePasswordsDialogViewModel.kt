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
import com.duckduckgo.autofill.impl.importing.AutofillImportLaunchSource
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.ImportPasswordsPixelSender
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.DeterminingFirstView
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.Importing
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.PreImport
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesViewModel(FragmentScope::class)
class ImportFromGooglePasswordsDialogViewModel @Inject constructor(
    private val credentialImporter: CredentialImporter,
    private val dispatchers: DispatcherProvider,
    private val importPasswordsPixelSender: ImportPasswordsPixelSender,
) : ViewModel() {

    fun onImportFlowFinishedSuccessfully(importSource: AutofillImportLaunchSource) {
        viewModelScope.launch(dispatchers.main()) {
            observeImportJob(importSource)
        }
    }

    private suspend fun observeImportJob(importSource: AutofillImportLaunchSource) {
        credentialImporter.getImportStatus().collect {
            when (it) {
                is ImportResult.InProgress -> {
                    logcat { "Import in progress" }
                    _viewState.value = ViewState(viewMode = Importing)
                }

                is ImportResult.Finished -> {
                    logcat { "Import finished: ${it.savedCredentials} imported. ${it.numberSkipped} skipped." }
                    fireImportSuccessPixel(savedCredentials = it.savedCredentials, numberSkipped = it.numberSkipped, importSource = importSource)
                    _viewState.value = ViewState(viewMode = ViewMode.ImportSuccess(it))
                }
            }
        }
    }

    fun onImportFlowFinishedWithError(reason: UserCannotImportReason, importSource: AutofillImportLaunchSource) {
        fireImportFailedPixel(reason, importSource)
        _viewState.value = ViewState(viewMode = ViewMode.ImportError)
    }

    fun onImportFlowCancelledByUser(stage: String, canShowPreImportDialog: Boolean, importSource: AutofillImportLaunchSource) {
        importPasswordsPixelSender.onUserCancelledImportWebFlow(stage, importSource)

        if (!canShowPreImportDialog) {
            _viewState.value = ViewState(viewMode = ViewMode.FlowTerminated)
        }
    }

    private fun fireImportSuccessPixel(savedCredentials: Int, numberSkipped: Int, importSource: AutofillImportLaunchSource) {
        importPasswordsPixelSender.onImportSuccessful(
            savedCredentials = savedCredentials,
            numberSkipped = numberSkipped,
            source = importSource,
        )
    }

    private fun fireImportFailedPixel(reason: UserCannotImportReason, importSource: AutofillImportLaunchSource) {
        importPasswordsPixelSender.onImportFailed(reason, importSource)
    }

    fun shouldShowInitialInstructionalPrompt(importSource: AutofillImportLaunchSource) {
        importPasswordsPixelSender.onImportPasswordsDialogDisplayed(importSource)
        _viewState.value = viewState.value.copy(viewMode = PreImport)
    }

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState

    data class ViewState(val viewMode: ViewMode = DeterminingFirstView)

    sealed interface ViewMode {
        data object DeterminingFirstView : ViewMode
        data object PreImport : ViewMode
        data object Importing : ViewMode
        data class ImportSuccess(val importResult: ImportResult.Finished) : ViewMode
        data object ImportError : ViewMode
        data object FlowTerminated : ViewMode
    }
}
