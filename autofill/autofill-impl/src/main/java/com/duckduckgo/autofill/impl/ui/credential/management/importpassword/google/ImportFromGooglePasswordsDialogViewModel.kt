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
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.ImportPasswordsPixelSender
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.BrowserPromoPreImport
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.DeterminingFirstView
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.Importing
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialogViewModel.ViewMode.PreImport
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class ImportFromGooglePasswordsDialogViewModel @Inject constructor(
    private val credentialImporter: CredentialImporter,
    private val dispatchers: DispatcherProvider,
    private val importPasswordsPixelSender: ImportPasswordsPixelSender,
    private val autofillStore: InternalAutofillStore,
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
                    _viewState.value = ViewState(viewMode = Importing)
                }

                is ImportResult.Finished -> {
                    logcat { "Import finished: ${it.savedCredentials} imported. ${it.numberSkipped} skipped." }
                    _viewState.value = ViewState(viewMode = ViewMode.ImportSuccess(it))
                }
            }
        }
    }

    fun onImportFlowFinishedWithError() {
        _viewState.value = ViewState(viewMode = ViewMode.ImportError)
    }

    fun onImportFlowCancelledByUser(canShowPreImportDialog: Boolean) {
        if (!canShowPreImportDialog) {
            _viewState.value = ViewState(viewMode = ViewMode.FlowTerminated)
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
        _viewState.value = viewState.value.copy(viewMode = viewMode)
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

    data class ViewState(val viewMode: ViewMode = DeterminingFirstView)

    sealed interface ViewMode {
        data object DeterminingFirstView : ViewMode
        data object PreImport : ViewMode
        data object BrowserPromoPreImport : ViewMode
        data object Importing : ViewMode
        data class ImportSuccess(val importResult: ImportResult.Finished) : ViewMode
        data object ImportError : ViewMode
        data object FlowTerminated : ViewMode
    }
}
