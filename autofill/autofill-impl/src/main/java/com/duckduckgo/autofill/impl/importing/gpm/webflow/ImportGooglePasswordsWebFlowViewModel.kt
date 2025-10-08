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

package com.duckduckgo.autofill.impl.importing.gpm.webflow

import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter.CsvCredentialImportResult
import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordConfigStore
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.Command.InjectCredentialsFromReauth
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.Command.NoCredentialsAvailable
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.Command.PromptUserToSelectFromStoredCredentials
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.UserCannotImportReason.ErrorParsingCsv
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.Initializing
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.UserCancelledImportFlow
import com.duckduckgo.autofill.impl.store.ReAuthenticationDetails
import com.duckduckgo.autofill.impl.store.ReauthenticationHandler
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class ImportGooglePasswordsWebFlowViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val credentialImporter: CredentialImporter,
    private val csvCredentialConverter: CsvCredentialConverter,
    private val autofillImportConfigStore: AutofillImportPasswordConfigStore,
    private val urlToStageMapper: ImportGooglePasswordUrlToStageMapper,
    private val reauthenticationHandler: ReauthenticationHandler,
    private val autofillFeature: AutofillFeature,
) : ViewModel() {

    private val _viewState = MutableStateFlow<ViewState>(Initializing)
    val viewState: StateFlow<ViewState> = _viewState

    private val _commands = MutableSharedFlow<Command>()
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    fun onViewCreated() {
        viewModelScope.launch(dispatchers.io()) {
            _viewState.value = ViewState.LoadStartPage(autofillImportConfigStore.getConfig().launchUrlGooglePasswords)
        }
    }

    suspend fun onCsvAvailable(csv: String) {
        when (val parseResult = csvCredentialConverter.readCsv(csv)) {
            is CsvCredentialImportResult.Success -> onCsvParsed(parseResult)
            is CsvCredentialImportResult.Error -> onCsvError()
        }
    }

    private suspend fun onCsvParsed(parseResult: CsvCredentialImportResult.Success) {
        credentialImporter.import(parseResult.loginCredentialsToImport, parseResult.numberCredentialsInSource)
        _viewState.value = ViewState.UserFinishedImportFlow
    }

    fun onCsvError() {
        logcat(WARN) { "Error decoding CSV" }
        _viewState.value = ViewState.UserFinishedCannotImport(ErrorParsingCsv)
    }

    fun onCloseButtonPressed(url: String?) {
        terminateFlowAsCancellation(url ?: "unknown")
    }

    fun onBackButtonPressed(
        url: String?,
        canGoBack: Boolean,
    ) {
        // if WebView can't go back, then we're at the first stage or something's gone wrong. Either way, time to cancel out of the screen.
        if (!canGoBack) {
            terminateFlowAsCancellation(url ?: "unknown")
            return
        }

        _viewState.value = ViewState.NavigatingBack
    }

    private fun terminateFlowAsCancellation(url: String) {
        viewModelScope.launch {
            _viewState.value = UserCancelledImportFlow(urlToStageMapper.getStage(url))
        }
    }

    fun firstPageLoading() {
        _viewState.value = ViewState.WebContentShowing
    }

    fun onCredentialsAvailableToSave(
        currentUrl: String,
        credentials: LoginCredentials,
    ) {
        storeReauthenticationDetails(currentUrl, credentials.password)
    }

    fun onCredentialsAutofilled(url: String, password: String?) {
        storeReauthenticationDetails(url, password)
    }

    private fun storeReauthenticationDetails(
        currentUrl: String,
        password: String?,
    ) {
        viewModelScope.launch {
            if (canReAuthenticate().not()) {
                logcat { "Re-authentication feature unavailable, not storing credentials for re-authentication" }
                return@launch
            }
            reauthenticationHandler.storeForReauthentication(currentUrl, password)

            logcat {
                "Storing credentials for re-authentication: " +
                    "password[${if (password.isNullOrBlank()) "blank" else "provided"}]"
            }
        }
    }

    fun onStoredCredentialsAvailable(
        originalUrl: String,
        credentials: List<LoginCredentials>,
        triggerType: LoginTriggerType,
        scenarioAllowsReAuthentication: Boolean,
    ) {
        viewModelScope.launch {
            logcat { "onStoredCredentialsAvailable. re-AuthAllowed=$scenarioAllowsReAuthentication, triggerType=$triggerType" }

            val reauthData = if (scenarioAllowsReAuthentication) getReauthData(originalUrl) else null
            if (reauthData?.password != null) {
                logcat { "Stored credentials available but using re-authentication details instead: $reauthData" }
                _commands.emit(
                    InjectCredentialsFromReauth(
                        url = originalUrl,
                        password = reauthData.password,
                    ),
                )
            } else {
                logcat { "No re-auth data available or permitted, prompting user to select stored credentials" }
                _commands.emit(
                    PromptUserToSelectFromStoredCredentials(
                        originalUrl = originalUrl,
                        credentials = credentials,
                        triggerType = triggerType,
                    ),
                )
            }
        }
    }

    suspend fun getReauthData(originalUrl: String): ReAuthenticationDetails? {
        return withContext(dispatchers.io()) {
            if (canReAuthenticate()) {
                reauthenticationHandler.retrieveReauthData(originalUrl)
            } else {
                null
            }
        }
    }

    fun onNoStoredCredentialsAvailable(originalUrl: String) {
        viewModelScope.launch {
            val reauthData = getReauthData(originalUrl)
            logcat { "No stored credentials available; checking re-authentication details: $reauthData" }

            if (reauthData?.password != null) {
                _commands.emit(
                    InjectCredentialsFromReauth(
                        url = originalUrl,
                        password = reauthData.password,
                    ),
                )
            } else {
                _commands.emit(NoCredentialsAvailable)
            }
        }
    }

    override fun onCleared() {
        reauthenticationHandler.clearAll()
    }

    private suspend fun canReAuthenticate(): Boolean {
        return withContext(dispatchers.io()) {
            autofillFeature.canReAuthenticateGoogleLoginsAutomatically().isEnabled()
        }
    }

    sealed interface ViewState {
        data object Initializing : ViewState
        data object WebContentShowing : ViewState
        data class LoadStartPage(val initialLaunchUrl: String) : ViewState
        data class UserCancelledImportFlow(val stage: String) : ViewState
        data object UserFinishedImportFlow : ViewState
        data class UserFinishedCannotImport(val reason: UserCannotImportReason) : ViewState
        data object NavigatingBack : ViewState
    }

    sealed interface Command {
        data class InjectCredentialsFromReauth(val url: String? = null, val username: String = "", val password: String?) : Command
        data class PromptUserToSelectFromStoredCredentials(
            val originalUrl: String,
            val credentials: List<LoginCredentials>,
            val triggerType: LoginTriggerType,
        ) : Command
        data object NoCredentialsAvailable : Command
    }

    sealed interface UserCannotImportReason : Parcelable {
        @Parcelize
        data object ErrorParsingCsv : UserCannotImportReason
    }

    sealed interface BackButtonAction {
        data object NavigateBack : BackButtonAction
    }
}
